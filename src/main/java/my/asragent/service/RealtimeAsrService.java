package my.asragent.service;

import com.alibaba.dashscope.audio.omni.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import my.asragent.ai.workflow.MainWorkFlowService;
import my.asragent.entity.TranslationResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Service
/**
 * 对接 DashScope 实时 ASR WebSocket API。
 *
 * 为每个 {@link AudioSession} 创建一个上游会话，并将服务端事件映射到内部回调。
 */
public class RealtimeAsrService {
    /** DashScope API Key，通常来自应用配置或环境变量。 */
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    /** 实时 ASR 模型 ID。 */
    @Value("${asr.realtime.model:qwen3-asr-flash-realtime}")
    private String model;

    /** 实时 WebSocket 端点。 */
    @Value("${asr.realtime.url:wss://dashscope.aliyuncs.com/api-ws/v1/realtime}")
    private String url;
    @Resource
    private VectorStore vectorStore;
    @Resource
    private LlmService llmService;
    @Resource
    private TranslationResultService translationResultService;
    @Resource
    private MainWorkFlowService mainWorkFlowService;
    @Resource
    private ExecutorService llmExecutorService;

    /** 记录每个 session 最近一次最终转写，用于拼接入向量库。 */
    private final Map<String, String> previousFinalTextMap = new ConcurrentHashMap<>();
    /** 记录每个 session 的累计转译全文，用于工作流队列推送。 */
    private final Map<String, String> fullTranslationMap = new ConcurrentHashMap<>();
    /** 会话工作流是否已启动标记。 */
    private final Map<String, Boolean> workflowStartedMap = new ConcurrentHashMap<>();

    /** 打开上游 ASR 会话并绑定到本地音频会话。 */
    public void openConversation(AudioSession audioSession) {
        log.info("ASR connect start: translationRecordId={}, model={}", audioSession.getTranslationRecordId(), model);
        OmniRealtimeParam param = OmniRealtimeParam.builder()
                .model(model)
                .url(url)
                .apikey(apiKey)
                .build();

        OmniRealtimeConversation conversation = new OmniRealtimeConversation(param, new OmniRealtimeCallback() {
            @Override
            public void onOpen() {
                log.info("ASR connected: translationRecordId={}", audioSession.getTranslationRecordId());
            }

            @Override
            public void onEvent(JsonObject message) {
                // 根据类型路由服务端事件。
                String type = getString(message, "type");
                if (type == null) {
                    return;
                }
                switch (type) {
                    case "conversation.item.input_audio_transcription.text" -> {
                        String text = getString(message, "text");
                        String stash = getString(message, "stash");
                        //使用滑动窗口裁切
                        audioSession.onPartial(text,stash);
                    }
                    case "conversation.item.input_audio_transcription.completed" -> {
                        // 兼容处理：优先使用 transcript，缺失时回退到 text 字段。
                        String text = getString(message, "transcript");
                        if (text == null) {
                            text = getString(message, "text");
                        }
                        audioSession.onFinal(text);
                        handleCompletedEvent(audioSession, text);
                    }
                    case "error" -> {
                        String error = getString(message, "message");
                        log.warn("ASR error event: translationRecordId={}, message={}", audioSession.getTranslationRecordId(), error);
                        audioSession.emitError("INTERNAL_ERROR", error == null ? "ASR error" : error);
                    }
                    default -> {
                    }
                }
            }

            @Override
            public void onClose(int code, String reason) {
                log.warn("ASR closed: translationRecordId={}, code={}, reason={}", audioSession.getTranslationRecordId(), code, reason);
                previousFinalTextMap.remove(audioSession.getTranslationRecordId());
                fullTranslationMap.remove(audioSession.getTranslationRecordId());
                workflowStartedMap.remove(audioSession.getTranslationRecordId());
                audioSession.emitError("INTERNAL_ERROR", "ASR closed: " + code + ":" + reason);
            }
        });

        try {
            conversation.connect();
        } catch (NoApiKeyException e) {
            // 除非修复配置，否则不可重试。
            log.warn("ASR connect failed: translationRecordId={}, message={}", audioSession.getTranslationRecordId(), e.getMessage());
            audioSession.emitError("INTERNAL_ERROR", "Missing DashScope API key");
            return;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        OmniRealtimeTranscriptionParam transcriptionParam = new OmniRealtimeTranscriptionParam();
        transcriptionParam.setLanguage(audioSession.getSourceLang());
        transcriptionParam.setInputAudioFormat("pcm");
        transcriptionParam.setInputSampleRate(audioSession.getSpec().sampleRate());

        OmniRealtimeConfig config = OmniRealtimeConfig.builder()
                .modalities(Collections.singletonList(OmniRealtimeModality.TEXT))
                .transcriptionConfig(transcriptionParam)
                .turnDetectionThreshold(0f)
                .turnDetectionSilenceDurationMs(400)
                .build();
        conversation.updateSession(config);
        audioSession.setConversation(conversation);
        log.info("ASR session configured: translationRecordId={}, sampleRate={}", audioSession.getTranslationRecordId(), audioSession.getSpec().sampleRate());
    }

    /** 用于服务端事件负载的空安全字符串提取器。 */
    private String getString(JsonObject obj, String field) {
        JsonElement element = obj.get(field);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    /** 处理最终转写完成事件：翻译、写库、向量入库、工作流入队。 */
    private void handleCompletedEvent(AudioSession audioSession, String currentFinalText) {
        if (audioSession.isClosed()) {
            // 会话已进入关闭兜底流程，跳过增量链路避免与完整快照写入竞争。
            return;
        }
        if (currentFinalText == null || currentFinalText.isBlank()) {
            return;
        }
        String normalizedCurrentText = currentFinalText.trim();
        String translationRecordId = audioSession.getTranslationRecordId();
        String previousFinalText = previousFinalTextMap.put(translationRecordId, normalizedCurrentText);
        String mergedFinalText = mergeFinalTexts(previousFinalText, normalizedCurrentText);
        BlockingQueue<String> blockingQueue = mainWorkFlowService.getOrCreateQueue(translationRecordId);
        startWorkflowIfNeeded(blockingQueue, audioSession.getUser_id(), translationRecordId);

        llmExecutorService.submit(() -> {
            String translationText = translateSafely(audioSession, normalizedCurrentText);
            String fullTranslationTextInMemory = appendFullTranslationText(translationRecordId, translationText);
            String fullTranslationText = appendTranslationResult(audioSession, normalizedCurrentText, translationText);
            String workflowText = (fullTranslationText == null || fullTranslationText.isBlank())
                    ? fullTranslationTextInMemory
                    : fullTranslationText;
            boolean offered = blockingQueue.offer(workflowText);
            if (!offered) {
                log.warn("Workflow queue full, text dropped: translationRecordId={}", translationRecordId);
            }
            persistVectorDocument(audioSession, previousFinalText, normalizedCurrentText, mergedFinalText, translationText);
        });
    }

    /** 进行安全翻译，失败时回退为原文，避免阻断后续链路。 */
    private String translateSafely(AudioSession audioSession, String text) {
        try {
            String result = llmService.translate(text, audioSession.getSourceLang(), audioSession.getTargetLang());
            if (result != null && !result.isBlank()) {
                return result.trim();
            }
        } catch (Exception ex) {
            log.warn("ASR completed translate failed: translationRecordId={}, message={}",
                    audioSession.getTranslationRecordId(), ex.getMessage());
        }
        return text;
    }

    /** 追加写入转写结果表中的原文与译文字段。 */
    private String appendTranslationResult(AudioSession audioSession, String finalText, String translationText) {
        Long recordId = parseSessionRecordId(audioSession.getTranslationRecordId());
        if (recordId == null) {
            log.warn("Skip append translation result: invalid translationRecordId={}", audioSession.getTranslationRecordId());
            return normalizeText(translationText);
        }
        Long userId = parseUserId(audioSession.getUser_id());

        try {
            QueryWrapper queryWrapper = QueryWrapper.create().eq("id", recordId);
            TranslationResult existing = translationResultService.getOne(queryWrapper);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            if (existing == null) {
                TranslationResult created = TranslationResult.builder()
                        .id(recordId)
                        .userId(userId)
                        .sseConnectionId(audioSession.getTranslationRecordId())
                        .fullText(normalizeText(finalText))
                        .translationText(normalizeText(translationText))
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                translationResultService.save(created);
                return created.getTranslationText();
            } else {
                if (userId != null) {
                    existing.setUserId(userId);
                }
                existing.setSseConnectionId(audioSession.getTranslationRecordId());
                existing.setFullText(appendText(existing.getFullText(), finalText));
                existing.setTranslationText(appendText(existing.getTranslationText(), translationText));
                existing.setUpdatedAt(now);
                translationResultService.updateById(existing);
                return existing.getTranslationText();
            }
        } catch (Exception ex) {
            log.warn("Append translation result failed: translationRecordId={}, message={}",
                    audioSession.getTranslationRecordId(), ex.getMessage());
            return normalizeText(translationText);
        }
    }

    /** 将“上一条最终文本 + 当前最终文本”写入向量库并附带业务元数据。 */
    private void persistVectorDocument(AudioSession audioSession,
                                       String previousFinalText,
                                       String currentFinalText,
                                       String mergedFinalText,
                                       String translationText) {
        if (mergedFinalText == null || mergedFinalText.isBlank()) {
            return;
        }
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("user_id", defaultUserId(audioSession.getUser_id()));
            metadata.put("translation_record_id", audioSession.getTranslationRecordId());
            metadata.put("source_lang", audioSession.getSourceLang());
            metadata.put("target_lang", audioSession.getTargetLang());
            metadata.put("previous_final_text", previousFinalText==null?"":previousFinalText);
            metadata.put("current_final_text", currentFinalText);
            metadata.put("translation_text", translationText);
            metadata.put("created_at", Instant.now().toString());
            metadata.put("vector_scope", "asr_final_pair");

            Document document = new Document(UUID.randomUUID().toString(), mergedFinalText, metadata);
            vectorStore.add(Collections.singletonList(document));
        } catch (Exception ex) {
            log.warn("Vector persist failed: translationRecordId={}, message={}", audioSession.getTranslationRecordId(), ex.getMessage());
        }
    }

    /** 启动会话工作流，仅首次 completed 事件触发一次。 */
    private void startWorkflowIfNeeded(BlockingQueue<String> blockingQueue, String userId, String translationRecordId) {
        if (workflowStartedMap.putIfAbsent(translationRecordId, Boolean.TRUE) != null) {
            return;
        }
        llmExecutorService.submit(() -> {
            try {
                mainWorkFlowService.startWorkFlow(blockingQueue, userId, translationRecordId);
            } catch (Exception ex) {
                workflowStartedMap.remove(translationRecordId);
                log.warn("Start workflow failed: translationRecordId={}, message={}", translationRecordId, ex.getMessage());
            }
        });
    }

    /** 合并上一条与当前条最终文本，作为向量入库文本。 */
    private String mergeFinalTexts(String previousFinalText, String currentFinalText) {
        if (previousFinalText == null || previousFinalText.isBlank()) {
            return normalizeText(currentFinalText);
        }
        return previousFinalText.trim() + " " + normalizeText(currentFinalText);
    }

    /** 将新文本追加到已有文本尾部。 */
    private String appendText(String existingText, String appendText) {
        String normalizedAppend = normalizeText(appendText);
        if (existingText == null || existingText.isBlank()) {
            return normalizedAppend;
        }
        if (normalizedAppend.isBlank()) {
            return existingText;
        }
        return existingText + " " + normalizedAppend;
    }

    /** 统一文本标准化，避免空值与首尾空白影响存储。 */
    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    /** 累计会话转译全文，供工作流只接收全文版本。 */
    private String appendFullTranslationText(String translationRecordId, String translationText) {
        String normalized = normalizeText(translationText);
        return fullTranslationMap.merge(translationRecordId, normalized, (oldValue, newValue) -> {
            if (oldValue == null || oldValue.isBlank()) {
                return newValue;
            }
            if (newValue == null || newValue.isBlank()) {
                return oldValue;
            }
            return oldValue + " " + newValue;
        });
    }

    /** 解析会话对应的记录 ID。 */
    private Long parseSessionRecordId(String translationRecordId) {
        if (translationRecordId == null || translationRecordId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(translationRecordId);
        } catch (Exception ex) {
            return null;
        }
    }

    /** 将字符串用户标识安全转换为数值标识。 */
    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            log.warn("Invalid user_id format: userId={}", userId);
            return null;
        }
    }

    /** 在缺失用户标识时提供默认值，便于向量检索区分。 */
    private String defaultUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "anonymous";
        }
        return userId;
    }
}
