package my.asragent.model;

/** WebSocket stop 命令负载。 */
public record StopMessage(String type, String translationRecordId) {
}

