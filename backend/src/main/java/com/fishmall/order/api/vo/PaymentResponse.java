package com.fishmall.order.api.vo;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        String outTradeNo,
        String transactionId,
        String state,
        Long amountCent,
        String tradeState,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
}
