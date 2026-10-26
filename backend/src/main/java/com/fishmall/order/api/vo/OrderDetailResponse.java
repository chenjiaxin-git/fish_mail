package com.fishmall.order.api.vo;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long id,
        String orderNo,
        Long wxUserId,
        String status,
        String source,
        String remark,
        AddressSnapshotResponse addressSnapshot,
        Long estimatedAmountCent,
        Long finalAmountCent,
        String currency,
        Integer quoteVersion,
        LocalDateTime quotedAt,
        LocalDateTime payDeadlineAt,
        LocalDateTime paidAt,
        LocalDateTime deliveredAt,
        LocalDateTime completedAt,
        LocalDateTime closedAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        String refundStatus,
        String refundReference,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderItemResponse> items,
        List<PriceRevisionResponse> priceRevisions,
        List<PaymentResponse> payments,
        List<OrderStatusLogResponse> statusLogs
) {
}
