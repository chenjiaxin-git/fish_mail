package com.fishmall.catalog.api.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String subtitle,
        String description,
        String mainImageUrl,
        String status,
        List<ProductSpecResponse> specs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
