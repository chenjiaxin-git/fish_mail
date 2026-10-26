package com.fishmall.catalog.api.vo;

import java.math.BigDecimal;

public record PublicProductSpecResponse(
        Long id,
        Long productId,
        String name,
        String quantityKind,
        String unit,
        Long priceCent,
        BigDecimal minQuantity,
        BigDecimal stepQuantity,
        BigDecimal availableQty
) {
}
