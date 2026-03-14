package my.asragent.utils;

import cn.hutool.core.util.StrUtil;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;

import my.asragent.config.MinioConfig;
import my.asragent.exception.BusinessException;
import my.asragent.exception.ErrorCode;
import my.asragent.exception.ThrowUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;

@Slf4j
@Component
public class MinioFileUploadUtil {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public MinioFileUploadUtil(MinioClient minioClient, MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
    }

    public String uploadFile(File file, String objectName) {
        return uploadFile(file, objectName, null);
    }

    public String uploadFile(File file, String objectName, String contentType) {
        ThrowUtils.throwIf(file == null || !file.exists() || file.isDirectory(),
                ErrorCode.PARAMS_ERROR, "上传文件不存在或不可用");
        ThrowUtils.throwIf(StrUtil.isBlank(objectName), ErrorCode.PARAMS_ERROR, "对象名称不能为空");
        String bucket = minioConfig.getBucket();
        ThrowUtils.throwIf(StrUtil.isBlank(bucket), ErrorCode.SYSTEM_ERROR, "MinIO bucket 未配置");

        String normalizedObjectName = normalizeObjectName(objectName);
        ensureBucketExists(bucket);

        String resolvedContentType = resolveContentType(file, contentType);
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(normalizedObjectName)
                    .stream(inputStream, file.length(), -1)
                    .contentType(resolvedContentType)
                    .build();
            minioClient.putObject(args);
        } catch (Exception e) {
            log.error("上传文件到 MinIO 失败: {} -> {}/{}", file.getAbsolutePath(), bucket, normalizedObjectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传文件到 MinIO 失败");
        }

        return buildObjectUrl(bucket, normalizedObjectName);
    }

    private void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.error("检查或创建 MinIO Bucket 失败: {}", bucket, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "MinIO Bucket 不可用");
        }
    }

    private String resolveContentType(File file, String contentType) {
        if (StrUtil.isNotBlank(contentType)) {
            return contentType;
        }
        try {
            String probed = Files.probeContentType(file.toPath());
            if (StrUtil.isNotBlank(probed)) {
                return probed;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "application/octet-stream";
    }

    private String normalizeObjectName(String objectName) {
        String normalized = objectName.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String buildObjectUrl(String bucket, String objectName) {
        String base = StrUtil.isNotBlank(minioConfig.getPublicEndpoint())
                ? minioConfig.getPublicEndpoint()
                : minioConfig.getEndpoint();
        if (StrUtil.isBlank(base)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "MinIO endpoint 未配置");
        }
        base = StrUtil.removeSuffix(base, "/");
        return base + "/" + bucket + "/" + objectName;
    }
}
