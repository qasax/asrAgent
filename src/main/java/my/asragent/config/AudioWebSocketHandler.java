package my.asragent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import my.asragent.model.AudioSpec;
import my.asragent.model.PingMessage;
import my.asragent.model.StartMessage;
import my.asragent.model.StopMessage;
import my.asragent.service.AudioSession;
import my.asragent.service.AudioSessionManager;
import my.asragent.service.RealtimeAsrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.Objects;

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
    /** 存储在 WebSocket 连接上的属性键，用于绑定到单个音频会话。 */
    private static final String ATTR_SESSION_ID = "audioSessionId";

    private final ObjectMapper objectMapper;
    private final AudioSessionManager sessionManager;
    private final RealtimeAsrService realtimeAsrService;

    public AudioWebSocketHandler(ObjectMapper objectMapper,
                                 AudioSessionManager sessionManager,
                                 RealtimeAsrService realtimeAsrService) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.realtimeAsrService = realtimeAsrService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 提高帧大小上限以支持预期的 PCM 分片负载。
        session.setBinaryMessageSizeLimit(1024 * 1024);
        log.info("WS connected: id={}, remote={}", session.getId(), session.getRemoteAddress());
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
        String sessionId = (String) session.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId == null) {
            log.warn("WS audio before start: wsId={}", session.getId());
            sendWsError(session, "SESSION_NOT_FOUND", "start is required before sending audio");
            return;
        }
        AudioSession audioSession = sessionManager.get(sessionId);
        if (audioSession == null) {
            log.warn("WS audio unknown session: wsId={}, sessionId={}", session.getId(), sessionId);
            sendWsError(session, "SESSION_NOT_FOUND", "Session not found: " + sessionId);
            return;
        }
        ByteBuffer payload = message.getPayload();
        byte[] audioBytes = new byte[payload.remaining()];
        payload.get(audioBytes);
        // 校验 20ms PCM 帧大小以保持 ASR 输入稳定。
        if (!audioSession.isChunkSizeValid(audioBytes.length)) {
            log.warn("WS audio invalid chunk: sessionId={}, size={}", sessionId, audioBytes.length);
            audioSession.emitError("INVALID_MESSAGE", "Invalid audio chunk size: " + audioBytes.length);
            return;
        }
        log.debug("WS audio frame: sessionId={}, size={}", sessionId, audioBytes.length);
        audioSession.appendAudio(audioBytes);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = (String) session.getAttributes().get(ATTR_SESSION_ID);
        log.info("WS closed: id={}, sessionId={}, status={}", session.getId(), sessionId, status);
        if (sessionId != null) {
            sessionManager.close(sessionId, "WS closed: " + status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = (String) session.getAttributes().get(ATTR_SESSION_ID);
        log.warn("WS transport error: id={}, sessionId={}, message={}", session.getId(), sessionId, exception.getMessage());
        if (sessionId != null) {
            sessionManager.close(sessionId, "WS error: " + exception.getMessage());
        }
        super.handleTransportError(session, exception);
    }

    private void handleStart(WebSocketSession session, JsonNode node) throws Exception {
        // 解析并校验 start 命令负载。
        StartMessage start = objectMapper.treeToValue(node, StartMessage.class);
        if (start.sessionId() == null || start.sessionId().isBlank()) {
            log.warn("WS start missing sessionId: wsId={}", session.getId());
            sendWsError(session, "INVALID_MESSAGE", "sessionId is required");
            return;
        }
        AudioSpec spec = start.audio();
        if (spec == null || !spec.isSupported()) {
            log.warn("WS start unsupported audio: sessionId={}", start.sessionId());
            sendWsError(session, "AUDIO_UNSUPPORTED", "Unsupported audio format");
            return;
        }
        if (sessionManager.exists(start.sessionId())) {
            // 防止两个 WebSocket 连接占用同一会话 ID。
            log.warn("WS start duplicate session: sessionId={}", start.sessionId());
            sendWsError(session, "SESSION_ALREADY_ACTIVE", "Session already active: " + start.sessionId());
            return;
        }
        // 创建本地会话、绑定 WebSocket，并建立上游 ASR 连接。
        AudioSession audioSession = sessionManager.create(start.sessionId(), spec, start.sourceLang(), start.targetLang());
        session.getAttributes().put(ATTR_SESSION_ID, start.sessionId());
        audioSession.bindWebSocket(session.getId());
        realtimeAsrService.openConversation(audioSession);
        log.info("WS start ok: wsId={}, sessionId={}", session.getId(), start.sessionId());
    }

    private void handleStop(WebSocketSession session, JsonNode node) throws Exception {
        // 解析 stop 命令，仅在匹配已绑定会话 ID 时关闭。
        StopMessage stop = objectMapper.treeToValue(node, StopMessage.class);
        String sessionId = stop.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("WS stop missing sessionId: wsId={}", session.getId());
            sendWsError(session, "INVALID_MESSAGE", "sessionId is required");
            return;
        }
        if (!Objects.equals(sessionId, session.getAttributes().get(ATTR_SESSION_ID))) {
            // 防止在共享 WebSocket 端点上跨会话发起 stop。
            log.warn("WS stop mismatched session: wsId={}, sessionId={}", session.getId(), sessionId);
            sendWsError(session, "SESSION_NOT_FOUND", "Session not bound to this connection");
            return;
        }
        log.info("WS stop: wsId={}, sessionId={}", session.getId(), sessionId);
        sessionManager.close(sessionId, "stop requested");
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

    /** 发送给客户端的最小 WebSocket 错误负载约定。 */
    private record WsError(String code, String message) {
    }
}
