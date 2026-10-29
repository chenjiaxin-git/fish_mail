package com.fishmall.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "storage.minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        @DefaultValue("fishmall-images") String bucket,
        @DefaultValue("products/") String prefix) {
    public MinioProperties {
        if (prefix == null || !prefix.matches("[a-z0-9-]+/"))
            throw new IllegalArgumentException("MinIO对象前缀不合法");
        if (bucket == null || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
            throw new IllegalArgumentException("MinIO桶名称不合法");
    }
}
