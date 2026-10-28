package com.fishmall.cart.api.vo;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productSpecId,
        Long productId,
        String productName,
        String specName,
        String quantityKind,
        String unit,
        Long priceCent,
        BigDecimal quantity,
        String mainImageUrl,
        BigDecimal availableQty,
        BigDecimal minQuantity,
        BigDecimal stepQuantity,
        boolean purchasable,
        String unavailableReason
) {
}
