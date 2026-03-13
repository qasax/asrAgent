package my.asragent.model;

/**
 * 客户端在 WebSocket `start` 时声明的 PCM 音频规格。
 *
 * 当前服务仅支持一种输入规格：
 * - 编码：pcm_s16le
 * - 采样率：16000
 * - 位深：16
 * - 声道：1
 * - 分片时长：20ms
 * - 每分片字节数：640
 */
public record AudioSpec(
        String codec,
        int sampleRate,
        int bitsPerSample,
        int channels,
        boolean signed,
        boolean littleEndian,
        int chunkDurationMs,
        int bytesPerChunk
) {
    /** 校验该规格是否匹配唯一支持的实时 ASR 规格。 */
    public boolean isSupported() {
        if (codec == null) {
            return false;
        }
        return "pcm_s16le".equalsIgnoreCase(codec)
                && sampleRate == 16000
                && bitsPerSample == 16
                && channels == 1
                && signed
                && littleEndian
                && chunkDurationMs == 20
                && bytesPerChunk == 640;
    }
}
