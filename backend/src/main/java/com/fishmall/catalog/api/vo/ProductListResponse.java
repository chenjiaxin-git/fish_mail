package com.fishmall.catalog.api.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductListResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String subtitle,
        String mainImageUrl,
        String status,
        int specCount,
        Long minPriceCent,
        Long maxPriceCent,
        BigDecimal totalPhysicalStockQty,
        BigDecimal totalReservedQty,
        BigDecimal totalAvailableQty,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
