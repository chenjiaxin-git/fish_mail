package com.fishmall.order.api.vo;

import java.time.LocalDateTime;

public record PriceRevisionResponse(
        Long id,
        Integer revisionNo,
        Long previousAmountCent,
        Long finalAmountCent,
        String reason,
        String itemsSnapshot,
        String adjustedByType,
        Long adjustedById,
        LocalDateTime createdAt
) {
}
