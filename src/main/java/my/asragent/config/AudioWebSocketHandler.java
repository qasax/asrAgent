package my.asragent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import my.asragent.constant.UserConstant;
import my.asragent.entity.TranslationResult;
import my.asragent.entity.User;
import my.asragent.exception.BusinessException;
import my.asragent.exception.ErrorCode;
import my.asragent.model.AudioSpec;
import my.asragent.model.PingMessage;
import my.asragent.model.StartMessage;
import my.asragent.model.StopMessage;
import my.asragent.service.AudioSession;
import my.asragent.service.AudioSessionManager;
import my.asragent.service.RealtimeAsrService;
import my.asragent.service.TranslationResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Timestamp;

import static my.asragent.constant.UserConstant.USER_LOGIN_STATE;

@Slf4j
@Component
/**
 * 处理实时音频采集的双向 WebSocket 通信。
 *
 * 协议概览：
 * - 文本帧控制生命周期：`start` / `stop` / `ping`
 * - 二进制帧在 `start` 成功后携带 PCM 分片
 * - 错误以 JSON 格式的 WebSocket 文本消息返回
 */
public class AudioWebSocketHandler extends AbstractWebSocketHandler {
    /** WebSocket 属性键：后端创建的转译记录 ID。 */
    private static final String ATTR_TRANSLATION_RECORD_ID = "translationRecordId";
    /** WebSocket 属性键：登录用户 ID。 */
    private static final String ATTR_USER_ID = "user_id";
    /** 建连阶段预创建会话使用的默认音频规格（与当前唯一支持规格一致）。 */
    private static final AudioSpec DEFAULT_AUDIO_SPEC = new AudioSpec(
            "pcm_s16le", 16000, 16, 1, true, true, 20, 640
    );

    private final ObjectMapper objectMapper;
    private final AudioSessionManager sessionManager;
    private final RealtimeAsrService realtimeAsrService;
    private final TranslationResultService translationResultService;

    public AudioWebSocketHandler(ObjectMapper objectMapper,
                                 AudioSessionManager sessionManager,
                                 RealtimeAsrService realtimeAsrService,
                                 TranslationResultService translationResultService) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.realtimeAsrService = realtimeAsrService;
        this.translationResultService = translationResultService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 提高帧大小上限以支持预期的 PCM 分片负载。
        session.setBinaryMessageSizeLimit(1024 * 1024);
        BigInteger loginUserId = resolveLoginUserId(session);
        if (loginUserId == null) {
            log.warn("WS rejected (not login): wsId={}, remote={}", session.getId(), session.getRemoteAddress());
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("NOT_LOGIN"));
            } catch (Exception ignored) {
            }
            return;
        }
        String recordId = createTranslationRecord(loginUserId, session.getId());
        if (recordId == null) {
            log.warn("WS rejected (init record failed): wsId={}", session.getId());
            try {
                session.close(CloseStatus.SERVER_ERROR.withReason("INIT_RECORD_FAILED"));
            } catch (Exception ignored) {
            }
            return;
        }
        session.getAttributes().put(ATTR_USER_ID, loginUserId.toString());
        session.getAttributes().put(ATTR_TRANSLATION_RECORD_ID, recordId);
        // 建连即预创建会话，确保前端立即连接 SSE 也能找到会话。
        AudioSession audioSession = sessionManager.create(recordId, DEFAULT_AUDIO_SPEC, "zh", "en");
        audioSession.setUser_id(loginUserId.toString());
        audioSession.setUserId(loginUserId);
        // 后端已准备好建立SSE连接，发送给前端连接id
        sendSessionCreated(session, recordId);
        log.info("WS connected: id={}, recordId={}, userId={}, remote={}",
                session.getId(), recordId, loginUserId, session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception ex) {
            // 尽早拒绝非法 JSON；客户端可用有效帧重试。
            sendWsError(session, "INVALID_MESSAGE", "Invalid JSON payload");
            return;
        }
        String type = node.path("type").asText(null);
        if (type == null) {
            sendWsError(session, "INVALID_MESSAGE", "Missing type");
            return;
        }
        switch (type) {
            // `start` 分配服务端会话并打开上游 ASR 会话。
            case "start" -> handleStart(session, node);
            // `stop` 关闭会话并触发摘要/问答流程收尾。
            case "stop" -> handleStop(session, node);
            // `ping` 维持客户端/服务端存活。
            case "ping" -> handlePing(session, node);
            default -> sendWsError(session, "INVALID_MESSAGE", "Unknown type: " + type);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // 只有在通过 `start` 绑定会话后才接受二进制音频。
        String recordId = (String) session.getAttributes().get(ATTR_TRANSLATION_RECORD_ID);
        String translationRecordId = recordId;
        if (translationRecordId == null) {
            log.warn("WS audio before start: wsId={}", session.getId());
            sendWsError(session, "SESSION_NOT_FOUND", "start is required before sending audio");
            return;
        }
        AudioSession audioSession = sessionManager.get(translationRecordId);
        if (audioSession == null) {
            log.warn("WS audio unknown session: wsId={}, translationRecordId={}", session.getId(), translationRecordId);
            sendWsError(session, "SESSION_NOT_FOUND", "Session not found: " + translationRecordId);
            return;
        }
        ByteBuffer payload = message.getPayload();
        byte[] audioBytes = new byte[payload.remaining()];
        payload.get(audioBytes);
        // 校验 20ms PCM 帧大小以保持 ASR 输入稳定。
        if (!audioSession.isChunkSizeValid(audioBytes.length)) {
            log.warn("WS audio invalid chunk: translationRecordId={}, size={}", translationRecordId, audioBytes.length);
            audioSession.emitError("INVALID_MESSAGE", "Invalid audio chunk size: " + audioBytes.length);
            return;
        }
        log.debug("WS audio frame: translationRecordId={}, size={}", translationRecordId, audioBytes.length);
        audioSession.appendAudio(audioBytes);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String recordId = (String) session.getAttributes().get(ATTR_TRANSLATION_RECORD_ID);
        log.info("WS closed: id={}, recordId={}, status={}", session.getId(), recordId, status);
        if (recordId != null) {
            sessionManager.close(recordId, "WS closed: " + status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String recordId = (String) session.getAttributes().get(ATTR_TRANSLATION_RECORD_ID);
        log.warn("WS transport error: id={}, recordId={}, message={}", session.getId(), recordId, exception.getMessage());
        if (recordId != null) {
            sessionManager.close(recordId, "WS error: " + exception.getMessage());
        }
        super.handleTransportError(session, exception);
    }

    private void handleStart(WebSocketSession session, JsonNode node) throws Exception {
        // 解析并校验 start 命令负载。
        StartMessage start = objectMapper.treeToValue(node, StartMessage.class);
        String recordId = (String) session.getAttributes().get(ATTR_TRANSLATION_RECORD_ID);
        if (recordId == null || recordId.isBlank()) {
            log.warn("WS start missing recordId: wsId={}", session.getId());
            sendWsError(session, "SESSION_NOT_FOUND", "backend recordId not initialized");
            return;
        }
        AudioSpec spec = start.audio();
        if (spec == null || !spec.isSupported()) {
            log.warn("WS start unsupported audio: recordId={}", recordId);
            sendWsError(session, "AUDIO_UNSUPPORTED", "Unsupported audio format");
            return;
        }
        // 使用建连阶段预创建会话；异常场景下兜底创建，避免空指针。
        AudioSession audioSession = sessionManager.get(recordId);
        if (audioSession == null) {
            audioSession = sessionManager.create(recordId, spec, start.sourceLang(), start.targetLang());
        }
        if (!audioSession.tryMarkStarted()) {
            log.warn("WS start duplicate session: recordId={}", recordId);
            sendWsError(session, "SESSION_ALREADY_ACTIVE", "Session already active: " + recordId);
            return;
        }
        // 在启动上游 ASR 前刷新语言配置。
        audioSession.configureLanguages(start.sourceLang(), start.targetLang());
        Object userObj = session.getAttributes().get(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        BigInteger userId = currentUser.getId();
        if (userId != null) {
            audioSession.setUser_id(userId.toString());
            audioSession.setUserId(userId);
        }
        realtimeAsrService.openConversation(audioSession);

        log.info("WS start ok: wsId={}, recordId={}, clientTranslationRecordId={}",
                session.getId(), recordId, start.translationRecordId());
    }

    private void handleStop(WebSocketSession session, JsonNode node) throws Exception {
        // 解析 stop 命令，仅在匹配已绑定会话 ID 时关闭。
        objectMapper.treeToValue(node, StopMessage.class);
        String recordId = (String) session.getAttributes().get(ATTR_TRANSLATION_RECORD_ID);
        if (recordId == null || recordId.isBlank()) {
            log.warn("WS stop missing recordId: wsId={}", session.getId());
            sendWsError(session, "SESSION_NOT_FOUND", "Session not bound to this connection");
            return;
        }
        log.info("WS stop: wsId={}, recordId={}", session.getId(), recordId);
        sessionManager.close(recordId, "stop requested");
    }

    private void handlePing(WebSocketSession session, JsonNode node) throws Exception {
        // 若提供时间戳则回显，否则使用服务器当前时间。
        PingMessage ping = objectMapper.treeToValue(node, PingMessage.class);
        long ts = ping.ts() == null ? System.currentTimeMillis() : ping.ts();
        String payload = objectMapper.writeValueAsString(new PingMessage("pong", ts));
        session.sendMessage(new TextMessage(payload));
        log.debug("WS ping/pong: wsId={}, ts={}", session.getId(), ts);
    }

    private void sendWsError(WebSocketSession session, String code, String message) throws Exception {
        // 统一的 WebSocket 控制面错误封装。
        String payload = objectMapper.writeValueAsString(new WsError(code, message));
        session.sendMessage(new TextMessage(payload));
        log.warn("WS error sent: wsId={}, code={}, message={}", session.getId(), code, message);
    }

    /** 解析并返回登录用户 ID，未登录返回 null。 */
    private BigInteger resolveLoginUserId(WebSocketSession session) {
        Object userObj = session.getAttributes().get(UserConstant.USER_LOGIN_STATE);
        if (userObj instanceof User user && user.getId() != null) {
            return user.getId();
        }
        Object userIdObj = session.getAttributes().get(ATTR_USER_ID);
        if (userIdObj == null) {
            return null;
        }
        try {
            return new BigInteger(userIdObj.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    /** 建连时预创建转译记录，并返回记录 ID。 */
    private String createTranslationRecord(BigInteger userId, String wsId) {
        try {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            TranslationResult record = TranslationResult.builder()
                    .userId(userId.longValueExact())
                    .translationText("")
                    .fullText("")
                    .sseConnectionId("ws:" + wsId)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            translationResultService.save(record);
            if (record.getId() == null) {
                return null;
            }
            record.setSseConnectionId(record.getId().toString());
            translationResultService.updateById(record);
            return record.getId().toString();
        } catch (Exception ex) {
            log.warn("Create translation record failed: wsId={}, message={}", wsId, ex.getMessage());
            return null;
        }
    }

    /** 将后端生成的会话记录 ID 回传给前端。 */
    private void sendSessionCreated(WebSocketSession session, String recordId) {
        try {
            String payload = objectMapper.writeValueAsString(new WsSessionCreated("session_created", recordId));
            session.sendMessage(new TextMessage(payload));
        } catch (Exception ex) {
            log.warn("Send session_created failed: wsId={}, message={}", session.getId(), ex.getMessage());
        }
    }

    /** 发送给客户端的最小 WebSocket 错误负载约定。 */
    private record WsError(String code, String message) {
    }

    /** 后端生成会话记录 ID 的通知载荷。 */
    private record WsSessionCreated(String type, String translationRecordId) {
    }
}
