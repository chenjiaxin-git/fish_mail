package com.fishmall.order.api.vo;

import java.time.LocalDateTime;

public record OrderStatusLogResponse(
        Long id,
        String fromStatus,
        String toStatus,
        String operatorType,
        Long operatorId,
        String reason,
        String source,
        LocalDateTime createdAt
) {
}
