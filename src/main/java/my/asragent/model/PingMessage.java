package my.asragent.model;

/** 用于 ping/pong 同步的 WebSocket 心跳消息。 */
public record PingMessage(String type, Long ts) {
}
