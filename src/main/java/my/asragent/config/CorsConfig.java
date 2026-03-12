package my.asragent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
/** REST/SSE 端点的全局 CORS 策略。 */
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 对所有端点应用 CORS 策略。
        registry.addMapping("/**")
                // 允许携带凭证的请求。
                .allowCredentials(true)
                // 使用模式以避免通配符与凭证冲突。
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }
}
