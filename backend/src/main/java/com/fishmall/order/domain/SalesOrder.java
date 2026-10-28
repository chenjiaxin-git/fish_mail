package com.fishmall.order.domain;

import java.time.LocalDateTime;

public class SalesOrder {
    private Long id;
    private String orderNo;
    private Long wxUserId;
    private String status;
    private String source;
    private String remark;
    private String addressSnapshot;
    private Long estimatedAmountCent;
    private Long finalAmountCent;
    private String currency;
    private Integer quoteVersion;
    private LocalDateTime quotedAt;
    private LocalDateTime payDeadlineAt;
    private LocalDateTime paidAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime completedAt;
    private LocalDateTime closedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String refundStatus;
    private String refundReference;
    private String idempotencyKey;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getWxUserId() { return wxUserId; }
    public void setWxUserId(Long wxUserId) { this.wxUserId = wxUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getAddressSnapshot() { return addressSnapshot; }
    public void setAddressSnapshot(String addressSnapshot) { this.addressSnapshot = addressSnapshot; }
    public Long getEstimatedAmountCent() { return estimatedAmountCent; }
    public void setEstimatedAmountCent(Long estimatedAmountCent) { this.estimatedAmountCent = estimatedAmountCent; }
    public Long getFinalAmountCent() { return finalAmountCent; }
    public void setFinalAmountCent(Long finalAmountCent) { this.finalAmountCent = finalAmountCent; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Integer getQuoteVersion() { return quoteVersion; }
    public void setQuoteVersion(Integer quoteVersion) { this.quoteVersion = quoteVersion; }
    public LocalDateTime getQuotedAt() { return quotedAt; }
    public void setQuotedAt(LocalDateTime quotedAt) { this.quotedAt = quotedAt; }
    public LocalDateTime getPayDeadlineAt() { return payDeadlineAt; }
    public void setPayDeadlineAt(LocalDateTime payDeadlineAt) { this.payDeadlineAt = payDeadlineAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
    public String getRefundReference() { return refundReference; }
    public void setRefundReference(String refundReference) { this.refundReference = refundReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OrderStatus status() {
        return OrderStatus.valueOf(status);
    }
}
