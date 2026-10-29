package com.fishmall.order.api.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 用户响应白名单，与后台审计和支付信息隔离。 */
public final class UserOrderResponse {
    private UserOrderResponse() { }

    public record Item(Long id, Long productId, Long productSpecId, String productName, String specName,
                       String productImageUrl, String quantityKind, String unit, Long unitPriceCent,
                       BigDecimal estimatedQuantity, BigDecimal actualQuantity,
                       Long estimatedAmountCent, Long finalAmountCent) { }

    public record Summary(String productName, String specName, String productImageUrl, String unit,
                          BigDecimal estimatedQuantity, BigDecimal actualQuantity) { }

    public record RevisionItem(Long orderItemId, BigDecimal actualQuantity, Long finalAmountCent) { }

    public record Revision(Integer revisionNo, Long previousAmountCent, Long finalAmountCent, String reason,
                           OffsetDateTime createdAt, List<RevisionItem> items) { }

    public record ListItem(Long id, String orderNo, String status, Long estimatedAmountCent, Long finalAmountCent,
                           Integer quoteVersion, OffsetDateTime payDeadlineAt, OffsetDateTime createdAt,
                           OffsetDateTime updatedAt, List<Summary> items) { }

    public record Detail(Long id, String orderNo, String status, String remark, String currency,
                         Integer quoteVersion, Long estimatedAmountCent, Long finalAmountCent,
                         AddressSnapshotResponse addressSnapshot, OffsetDateTime createdAt,
                         OffsetDateTime updatedAt, OffsetDateTime quotedAt, OffsetDateTime payDeadlineAt,
                         OffsetDateTime paidAt, OffsetDateTime deliveredAt, OffsetDateTime completedAt,
                         OffsetDateTime cancelledAt, OffsetDateTime closedAt, String cancelReason,
                         String refundStatus, List<Item> items, List<Revision> priceRevisions,
                         boolean canConfirmQuote, boolean canCancel) { }
}
