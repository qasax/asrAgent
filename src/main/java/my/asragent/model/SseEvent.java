package my.asragent.model;

/** 在每会话回放历史中持久化的内部 SSE 事件对象。 */
public record SseEvent(long id, String data) {
}
