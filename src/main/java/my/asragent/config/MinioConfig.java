package my.asragent.config;

import cn.hutool.core.util.StrUtil;
import io.minio.MinioClient;
import lombok.Data;
import my.asragent.exception.BusinessException;
import my.asragent.exception.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioConfig {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucket;

    /**
     * MinIO 对外访问地址（可选，未配置时使用 endpoint）
     */
    private String publicEndpoint;

    @Bean
    public MinioClient minioClient() {
        if (StrUtil.isBlank(endpoint) || StrUtil.isBlank(accessKey) || StrUtil.isBlank(secretKey)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "MinIO 配置不完整，请检查 minio.endpoint/access-key/secret-key");
        }
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
