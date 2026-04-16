package com.zhiwiki.document.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinIOConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private boolean secure;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .secure(secure)
                .build();
    }
}
