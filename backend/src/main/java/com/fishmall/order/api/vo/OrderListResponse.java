package com.fishmall.order.api.vo;

import java.time.LocalDateTime;

public record OrderListResponse(
        Long id,
        String orderNo,
        Long wxUserId,
        String status,
        String contactName,
        String phone,
        String remark,
        Long estimatedAmountCent,
        Long finalAmountCent,
        String refundStatus,
        String refundReference,
        LocalDateTime payDeadlineAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
