package com.fishmall.catalog.api.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductSpecResponse(
        Long id,
        Long productId,
        String name,
        String quantityKind,
        String unit,
        Long priceCent,
        BigDecimal minQuantity,
        BigDecimal stepQuantity,
        BigDecimal physicalStockQty,
        BigDecimal reservedQty,
        BigDecimal availableQty,
        BigDecimal warningQty,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
