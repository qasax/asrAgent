package my.asragent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
/** 注册实时音频上传的 WebSocket 端点。 */
public class WebSocketConfig implements WebSocketConfigurer {
    private final AudioWebSocketHandler audioWebSocketHandler;

    public WebSocketConfig(AudioWebSocketHandler audioWebSocketHandler) {
        this.audioWebSocketHandler = audioWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 单一 WebSocket 端点承载控制消息与二进制音频帧。
        registry.addHandler(audioWebSocketHandler, "/ws/audio")
                .setAllowedOrigins("*");
    }
}
