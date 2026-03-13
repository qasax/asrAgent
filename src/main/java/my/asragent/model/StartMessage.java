package my.asragent.model;

/** WebSocket start 命令负载。 */
public record StartMessage(String type, String translationRecordId, AudioSpec audio, String sourceLang, String targetLang) {
}

