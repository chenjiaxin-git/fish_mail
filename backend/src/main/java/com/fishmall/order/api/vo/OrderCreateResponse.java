package com.fishmall.order.api.vo;

public record OrderCreateResponse(
        Long id,
        String orderNo,
        String status,
        Long estimatedAmountCent
) {
}
