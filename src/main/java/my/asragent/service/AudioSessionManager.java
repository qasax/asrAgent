package my.asragent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import my.asragent.ai.workflow.MainWorkFlowService;
import my.asragent.model.AudioSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import my.asragent.ai.service.AnalyseService;
import my.asragent.service.TranslationResultService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
/**
 * 活动 {@link AudioSession} 实例的线程安全注册表。
 *
 * 该管理器是本服务中会话生命周期的唯一所有者。
 */
public class AudioSessionManager {
    /** 以内存保存的活动会话表，按业务会话 ID 索引。 */
    private final Map<String, AudioSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final LlmService llmService;
    private final TranslationResultService translationResultService;
    private final AnalyseService analyseService;
    private final MainWorkFlowService mainWorkFlowService;
    private final VectorStore vectorStore;
    private final ExecutorService llmExecutor;

    public AudioSessionManager(ObjectMapper objectMapper,
                               LlmService llmService,
                               TranslationResultService translationResultService,
                               AnalyseService analyseService,
                               MainWorkFlowService mainWorkFlowService,
                               VectorStore vectorStore,
                               ExecutorService llmExecutor) {
        this.objectMapper = objectMapper;
        this.llmService = llmService;
        this.translationResultService = translationResultService;
        this.analyseService = analyseService;
        this.mainWorkFlowService = mainWorkFlowService;
        this.vectorStore = vectorStore;
        this.llmExecutor = llmExecutor;
    }

    /** 判断会话 ID 是否已处于活动状态。 */
    public boolean exists(String translationRecordId) {
        return sessions.containsKey(translationRecordId);
    }

    /** 按 ID 获取会话，不存在则返回 null。 */
    public AudioSession get(String translationRecordId) {
        return sessions.get(translationRecordId);
    }

    /** 为进入的 WebSocket 音频流创建并注册新会话。 */
    public AudioSession create(String translationRecordId, AudioSpec spec, String sourceLang, String targetLang) {
        AudioSession audioSession = new AudioSession(
                translationRecordId,
                spec,
                objectMapper,
                llmService,
                translationResultService,
                analyseService,
                mainWorkFlowService,
                vectorStore,
                llmExecutor,
                sourceLang,
                targetLang
        );
        sessions.put(translationRecordId, audioSession);
        log.info("Session created: translationRecordId={}", translationRecordId);
        return audioSession;
    }

    /** 若会话存在则移除并关闭。 */
    public void close(String translationRecordId, String reason) {
        AudioSession audioSession = sessions.remove(translationRecordId);
        if (audioSession != null) {
            log.info("Session closing: translationRecordId={}, reason={}", translationRecordId, reason);
            audioSession.close(reason);
        } else {
            log.warn("Session close requested but not found: translationRecordId={}, reason={}", translationRecordId, reason);
        }
    }
}

