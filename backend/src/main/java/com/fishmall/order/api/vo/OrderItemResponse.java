package com.fishmall.order.api.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderItemResponse(
        Long id,
        Long productId,
        Long productSpecId,
        String productName,
        String specName,
        String quantityKind,
        String unit,
        Long unitPriceCent,
        BigDecimal estimatedQuantity,
        BigDecimal actualQuantity,
        Long estimatedAmountCent,
        Long finalAmountCent,
        String productImageUrl,
        LocalDateTime createdAt
) {
}
