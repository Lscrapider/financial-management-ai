package com.scrapider.finance.ai.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioClientConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${finance.minio.endpoint}") String endpoint,
            @Value("${finance.minio.access-key}") String accessKey,
            @Value("${finance.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
