package com.fishmall.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product")
public record ProductStorageProperties(
        String imageDir,
        String imageBaseUrl,
        long imageMaxBytes
) {
    public ProductStorageProperties {
        if (imageDir == null || imageDir.isBlank()) {
            imageDir = "./data/product-images";
        }
        if (imageBaseUrl == null || imageBaseUrl.isBlank()) {
            imageBaseUrl = "/uploads/products";
        }
        if (!imageBaseUrl.startsWith("/")) {
            imageBaseUrl = "/" + imageBaseUrl;
        }
        imageBaseUrl = imageBaseUrl.replaceAll("/+$", "");
        if (imageMaxBytes <= 0) {
            imageMaxBytes = 5L * 1024 * 1024;
        }
    }
}
