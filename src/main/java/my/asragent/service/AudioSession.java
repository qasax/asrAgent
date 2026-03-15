package my.asragent.service;

import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.service.AnalyseService;
import my.asragent.ai.workflow.MainWorkFlowService;
import my.asragent.entity.TranslationResult;
import my.asragent.model.AudioSpec;
import my.asragent.model.QaResult;
import my.asragent.model.ServerMessage;
import my.asragent.model.SseEvent;
import my.asragent.service.TranslationResultService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
/**
 * 单个实时 ASR 会话的运行时状态容器。
 *
 * 职责：
 * - 接收 WebSocket PCM 分片并转发给上游 ASR
 * - 向 SSE 客户端推送增量/最终识别事件
 * - 异步执行 LLM 后处理（翻译、摘要、问答）
 * - 维护短期事件历史用于 SSE 断线重连回放
 */
@Data
public class AudioSession {
    /**
     * 用于回放的最近 SSE 事件最大保留数。
     */
    private static final int HISTORY_LIMIT = 200;

    /**
     * 客户端在 WebSocket `start` 中提供的业务会话 ID。
     */
    private final String translationRecordId;
    /**
     * 客户端声明的音频约束，用于校验分片形态。
     */
    private final AudioSpec spec;
    private final ObjectMapper objectMapper;
    private final LlmService llmService;
    private final TranslationResultService translationResultService;
    private final AnalyseService analyseService;
    private final MainWorkFlowService mainWorkFlowService;
    private final VectorStore vectorStore;
    private final ExecutorService virtualExecutor;
    private volatile String sourceLang;
    private volatile String targetLang;
    private final AtomicBoolean translating = new AtomicBoolean(false);
    /**
     * SSE `id` 字段使用的单调递增事件 ID。
     */
    private final AtomicLong eventSeq = new AtomicLong(0);
    /**
     * 用于 Last-Event-ID 回放的环形历史（按 HISTORY_LIMIT 裁剪）。
     */
    private final Deque<SseEvent> history = new ArrayDeque<>();
    private final Object historyLock = new Object();
    /**
     * 累计最终转写片段直到会话关闭。
     */
    private final StringBuilder transcript = new StringBuilder();
    private final StringBuilder translatedText = new StringBuilder();
    private final StringBuilder quickTranslatedText = new StringBuilder();
    private final Object translationLock = new Object();
    private final Object quickTranslationLock = new Object();
    /**
     * 保证 close() 在竞争关闭路径下幂等的保护。
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /**
     * 标记是否已执行过 start，防止重复启动上游 ASR。
     */
    private final AtomicBoolean started = new AtomicBoolean(false);
    /**
     * 关闭前等待上游回传最终转写事件的缓冲时间。
     */
    private static final long FINAL_FLUSH_WAIT_MS = 500L;

    /**
     * 当前 SSE 订阅者，重连时替换。
     */
    private volatile SseEmitter emitter;
    /**
     * 上游 DashScope 实时会话。
     */
    private volatile OmniRealtimeConversation conversation;
    /**
     * 预留给向量库/普通表存储使用的用户标识。
     */
    private volatile String user_id;
    private volatile BigInteger userId;

    // 中日韩语言代码前缀
    private static final Set<String> CJK_LANGS = Set.of("zh", "ja", "ko", "yue");

    public AudioSession(String translationRecordId,
                        AudioSpec spec,
                        ObjectMapper objectMapper,
                        LlmService llmService,
                        TranslationResultService translationResultService,
                        AnalyseService analyseService,
                        MainWorkFlowService mainWorkFlowService,
                        VectorStore vectorStore,
                        ExecutorService virtualExecutor,
                        String sourceLang,
                        String targetLang) {
        this.translationRecordId = translationRecordId;
        this.spec = spec;
        this.objectMapper = objectMapper;
        this.llmService = llmService;
        this.translationResultService = translationResultService;
        this.analyseService = analyseService;
        this.mainWorkFlowService = mainWorkFlowService;
        this.vectorStore = vectorStore;
        this.virtualExecutor = virtualExecutor;
        this.sourceLang = normalizeLang(sourceLang, "zh");
        this.targetLang = normalizeLang(targetLang, "en");
    }

    public void setConversation(OmniRealtimeConversation conversation) {
        this.conversation = conversation;
        log.info("ASR conversation ready: translationRecordId={}", translationRecordId);
    }

    /**
     * 尝试标记会话启动，只允许首个 start 成功。
     */
    public boolean tryMarkStarted() {
        return started.compareAndSet(false, true);
    }

    /**
     * 根据 start 请求刷新语种配置。
     */
    public void configureLanguages(String sourceLang, String targetLang) {
        this.sourceLang = normalizeLang(sourceLang, "zh");
        this.targetLang = normalizeLang(targetLang, "en");
    }

    /**
     * 绑定 SSE 订阅用户，用于防止跨用户误订阅。
     */
    public void bindSse(BigInteger userId) {
        if (userId == null) {
            return;
        }
        if (this.userId != null && !this.userId.equals(userId)) {
            log.warn("SSE bind user mismatch: translationRecordId={}, existingUserId={}, newUserId={}",
                    translationRecordId, this.userId, userId);
            return;
        }
        this.userId = userId;
        this.user_id = userId.toString();
    }

    /**
     * 返回会话是否已进入关闭流程。
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 根据协商规格校验进入的 WebSocket 音频帧大小。
     */
    public boolean isChunkSizeValid(int size) {
        return size > 0 && size <= spec.bytesPerChunk();
    }

    /**
     * 编码并转发一个 PCM 帧到实时 ASR。
     */
    public void appendAudio(byte[] pcmBytes) {
        OmniRealtimeConversation current = this.conversation;
        if (current == null || closed.get()) {
            log.warn("Append audio ignored: translationRecordId={}, closed={}", translationRecordId, closed.get());
            emitError("SESSION_NOT_FOUND", "Session not ready for audio");
            return;
        }
        String b64 = Base64.getEncoder().encodeToString(pcmBytes);
        current.appendAudio(b64);
    }

    /**
     * 以低延迟 SSE 事件发送非最终转写文本。
     */
    public void onPartial(String text, String stash) {
        if (text == null || text.isBlank()) {
            return;
        }
        String fullText = text + stash;
        //滑动窗口处理
        String windowText = getAdaptiveWindow(fullText, this.sourceLang);
        log.debug("ASR partial: translationRecordId={}, text={} stash={}", translationRecordId, text, stash);
        emit(ServerMessage.partial(windowText));
        //快速翻译
        triggerQuickTranslation(fullText, windowText);
    }

    /**
     * 发送最终分片，追加到转写文本后触发翻译。
     */
    public void onFinal(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        synchronized (transcript) {
            transcript.append(text).append(' ');
        }
        if (closed.get()) {
            log.debug("ASR final accepted during close: translationRecordId={}, text={}", translationRecordId, text);
            return;
        }
        log.info("ASR final: translationRecordId={}, text={}", translationRecordId, text);
        //滑动窗口处理--对于部分较短的语句，不会走part返回，而是在final一次性返回，需要特殊处理
        String windowText = getAdaptiveWindow(text, this.sourceLang);
        emit(ServerMessage.partial(windowText));

        emit(ServerMessage.finalText(text));
        triggerTranslation(text);
    }

    /**
     * 会话结束时调用一次，执行转写级后处理。
     */
    public void onStop() {
        String fullText;
        synchronized (transcript) {
            fullText = transcript.toString().trim();
        }
        log.info("Session stop: translationRecordId={}, transcriptLength={}", translationRecordId, fullText.length());
    }

    /**
     * 向 SSE 订阅者发送标准化服务端错误事件。
     */
    public void emitError(String code, String message) {
        log.warn("SSE error: translationRecordId={}, code={}, message={}", translationRecordId, code, message);
        emit(ServerMessage.error(code, message));
    }

    /**
     * 绑定/重新绑定 SSE emitter，并可选回放遗漏事件。
     *
     * @param emitter     控制器创建的活动 emitter
     * @param lastEventId 来自 `Last-Event-ID` 的回放游标
     */
    public void attachEmitter(SseEmitter emitter, Long lastEventId) {
        this.emitter = emitter;
        emitter.onCompletion(() -> {
            log.info("SSE completed: translationRecordId={}", translationRecordId);
            this.emitter = null;
        });
        emitter.onTimeout(() -> {
            log.warn("SSE timeout: translationRecordId={}", translationRecordId);
            this.emitter = null;
        });
        emitter.onError(ex -> {
            log.warn("SSE error: translationRecordId={}, message={}", translationRecordId, ex.getMessage());
            this.emitter = null;
        });

        if (lastEventId != null) {
            List<SseEvent> replay;
            synchronized (historyLock) {
                replay = history.stream().filter(ev -> ev.id() > lastEventId).toList();
            }
            // 先回放，再继续推送实时事件。
            log.info("SSE replay: translationRecordId={}, from={}, count={}", translationRecordId, lastEventId, replay.size());
            for (SseEvent event : replay) {
                sendEvent(emitter, event);
            }
        }
    }

    /**
     * 确保仅关闭一次会话并释放外部资源。
     * <p>
     * 副作用：
     * - 触发摘要/问答流程
     * - 结束上游 ASR 会话
     * - 结束 SSE 流
     */
    public void close(String reason) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        log.info("Session closed: translationRecordId={}, reason={}", translationRecordId, reason);
        if (conversation != null) {
            try {
                conversation.endSession();
            } catch (Exception ignored) {
            }
        }
        waitForFinalFlush();
        onStop();
        persistTranslationIfNeeded();
        // 通知当前会话工作流结束，解除阻塞等待并进入后续流程。
        mainWorkFlowService.notifyFinish(translationRecordId);
        mainWorkFlowService.removeQueue(translationRecordId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    /**
     * 序列化载荷，写入回放历史，并推送到当前 SSE 客户端。
     */
    private void emit(Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            json = "{\"type\":\"error\",\"message\":\"serialization_failed\"}";
        }
        long id = eventSeq.incrementAndGet();
        SseEvent event = new SseEvent(id, json);
        synchronized (historyLock) {
            history.addLast(event);
            while (history.size() > HISTORY_LIMIT) {
                history.removeFirst();
            }
        }
        SseEmitter current = this.emitter;
        if (current != null) {
            sendEvent(current, event);
        }
    }

    /**
     * 向 SSE 客户端发送事件；失败则清空 emitter 以便重连。
     */
    private void sendEvent(SseEmitter emitter, SseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(event.id()))
                    .data(event.data()));
        } catch (Exception ex) {
            log.warn("SSE send failed: translationRecordId={}, eventId={}, message={}", translationRecordId, event.id(), ex.getMessage());
            this.emitter = null;
        }
    }

    /**
     * 对每个最终分片异步执行翻译任务。
     */
    private void triggerTranslation(String text) {
        virtualExecutor.submit(() -> {
            try {
                log.debug("LLM translation start: translationRecordId={}", translationRecordId);
                String result = llmService.translate(text, sourceLang, targetLang);
                if (result != null && !result.isBlank()) {
                    synchronized (translationLock) {
                        translatedText.append(result).append(' ');
                    }
                    //部分较短的句子，直接在final返回了，因此也需要给part推一份，以供前端显示使用
                    emit(ServerMessage.quickTranslation(getAdaptiveWindow(result, this.targetLang)));
                    emit(ServerMessage.translation(result));
                    log.debug("LLM translation done: translationRecordId={}", translationRecordId);
                }
            } catch (Exception ex) {
                log.warn("LLM translation failed: translationRecordId={}, message={}", translationRecordId, ex.getMessage());
                emitError("INTERNAL_ERROR", "Translation failed");
            }
        });
    }

    /**
     * 异步执行快速翻译任务。
     */
    private void triggerQuickTranslation(String fullText, String translateText) {
        // 如果正在翻译，上一次任务还没完成，就直接跳过
        if (!translating.compareAndSet(false, true)) {
            log.debug("Skipping Quick Translation, previous task still running: translationRecordId={}", translationRecordId);
            return;
        }
        virtualExecutor.submit(() -> {
            try {
                log.debug("LLM Quick translation start: translationRecordId={}", translationRecordId);
                llmService.quickTranslate(fullText, translateText, sourceLang, targetLang).blockingForEach(translatedText -> {
                    quickTranslatedText.append(translatedText.getOutput().getChoices().get(0).getMessage().getContent());
                    emit(ServerMessage.quickTranslation(getAdaptiveWindow(quickTranslatedText.toString(), this.targetLang)));
                });
                Thread.sleep(100);
                log.debug("LLM Quick translation end: translationRecordId={}", translationRecordId);
            } catch (Exception ex) {
                log.warn("LLM Quick translation failed: translationRecordId={}, message={}", translationRecordId, ex.getMessage());
                emitError("INTERNAL_ERROR", "Quick Translation failed");
            } finally {
                translating.set(false);
            }
        });
    }


    /**
     * 关闭会话时执行完整翻译落库与向量入库兜底，确保断连后数据完整。
     */
    private void persistTranslationIfNeeded() {
        BigInteger currentUserId = this.userId;
        if (currentUserId == null) {
            log.info("Skip translation persist (not logged in): translationRecordId={}", translationRecordId);
            return;
        }
        String fullText;
        synchronized (transcript) {
            fullText = transcript.toString().trim();
        }
        if (fullText.isBlank()) {
            log.info("Skip translation persist (empty transcript): translationRecordId={}", translationRecordId);
            return;
        }
        try {
            String translation = llmService.translate(fullText, sourceLang, targetLang);
            if (translation == null || translation.isBlank()) {
                synchronized (translationLock) {
                    translation = translatedText.toString().trim();
                }
            }
            if (translation == null || translation.isBlank()) {
                translation = fullText;
            }
            Long userIdAsLong;
            try {
                userIdAsLong = currentUserId.longValueExact();
            } catch (ArithmeticException ex) {
                log.warn("Translation persist skipped (userId overflow): translationRecordId={}, userId={}",
                        translationRecordId, currentUserId);
                return;
            }
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Long recordId = parseSessionRecordId();
            if (recordId == null) {
                log.warn("Translation persist skipped (invalid translationRecordId): translationRecordId={}", translationRecordId);
                return;
            }
            QueryWrapper queryWrapper = QueryWrapper.create().eq("id", recordId);
            TranslationResult record = translationResultService.getOne(queryWrapper);
            if (record == null) {
                record = TranslationResult.builder()
                        .id(recordId)
                        .userId(userIdAsLong)
                        .sseConnectionId(translationRecordId)
                        .createdAt(now)
                        .build();
            }
            record.setFullText(fullText);
            record.setTranslationText(translation.trim());
            record.setUserId(userIdAsLong);
            record.setSseConnectionId(translationRecordId);
            record.setUpdatedAt(now);
            if (record.getId() == null) {
                record.setCreatedAt(now);
                translationResultService.save(record);
            } else {
                translationResultService.updateById(record);
            }
            persistVectorSnapshot(fullText, translation.trim());
            //早期方法。废弃
            //analyseService.analyse(translation.trim(), currentUserId.toString(), translationRecordId);
            log.info("Translation persisted on close: translationRecordId={}, userId={}",
                    translationRecordId, currentUserId);
        } catch (Exception ex) {
            log.warn("Translation persist failed: translationRecordId={}, message={}", translationRecordId, ex.getMessage());
        }
    }

    /**
     * 将会话完整快照写入向量库，用于断连后的完整检索。
     */
    private void persistVectorSnapshot(String fullText, String translationText) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("user_id", user_id == null || user_id.isBlank() ? "anonymous" : user_id);
            metadata.put("translation_record_id", translationRecordId);
            metadata.put("source_lang", sourceLang);
            metadata.put("target_lang", targetLang);
            metadata.put("vector_scope", "session_close_full");
            metadata.put("created_at", Instant.now().toString());
            metadata.put("translation_text", translationText);
            String content = "原文:\n" + fullText + "\n译文:\n" + translationText;
            Document document = new Document(UUID.randomUUID().toString(), content, metadata);
            vectorStore.add(Collections.singletonList(document));
            log.info("Vector snapshot persisted: translationRecordId={}", translationRecordId);
        } catch (Exception ex) {
            log.warn("Vector snapshot persist failed: translationRecordId={}, message={}", translationRecordId, ex.getMessage());
        }
    }

    /**
     * 等待上游 ASR 将缓冲中的最终文本回传，减少断连时遗漏。
     */
    private void waitForFinalFlush() {
        try {
            Thread.sleep(FINAL_FLUSH_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解析当前会话对应的转译记录 ID。
     */
    private Long parseSessionRecordId() {
        try {
            return Long.valueOf(translationRecordId);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeLang(String lang, String fallback) {
        if (lang == null || lang.isBlank()) {
            return fallback;
        }
        return lang.trim().toLowerCase();
    }

    /**
     * 根据语系智能获取滑动窗口内容
     *
     * @param content  全量文本
     * @param langCode 语言代码 (如 "en-US", "zh-CN")
     * @return 截断后的字幕
     */
    public static String getAdaptiveWindow(String content, String langCode) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // 1. 确定是否为 CJK 语系
        boolean isCjk = isCjkByLangOrContent(content, langCode);

        // 2. 设置阈值：CJK 40字, 西文 120字符
        int limit = isCjk ? 40 : 100;
        if (content.length() <= limit) {
            return content;
        }

        // 3. 计算起始位置
        int startIndex = content.length() - limit;

        // 4. 西文逻辑优化：避免从单词中间切断
        if (!isCjk) {
            // 向后寻找第一个空格，确保窗口开始处是一个完整的单词
            int firstSpace = content.indexOf(" ", startIndex);
            // 如果空格在合理范围内（不要缩短太多），则从空格后开始
            if (firstSpace != -1 && firstSpace < content.length() - 10) {
                startIndex = firstSpace + 1;
            }
        }

        return content.substring(startIndex);
    }

    private static boolean isCjkByLangOrContent(String content, String langCode) {
        // 优先根据语言代码判断
        if (langCode != null && langCode.length() >= 2) {
            String prefix = langCode.substring(0, 2).toLowerCase();
            if (CJK_LANGS.contains(prefix)) {
                return true;
            }
        }

        // 备选方案：检查前几个字符是否包含汉字/假名/谚文
        int checkLen = Math.min(content.length(), 10);
        for (int i = 0; i < checkLen; i++) {
            if (isCjkChar(content.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCjkChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
}
