package com.fishmall.order.domain;

import java.time.LocalDateTime;

public class OrderPriceRevision {
    private Long id;
    private Long orderId;
    private Integer revisionNo;
    private Long previousAmountCent;
    private Long finalAmountCent;
    private String reason;
    private String itemsSnapshot;
    private String adjustedByType;
    private Long adjustedById;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Long getPreviousAmountCent() { return previousAmountCent; }
    public void setPreviousAmountCent(Long previousAmountCent) { this.previousAmountCent = previousAmountCent; }
    public Long getFinalAmountCent() { return finalAmountCent; }
    public void setFinalAmountCent(Long finalAmountCent) { this.finalAmountCent = finalAmountCent; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getItemsSnapshot() { return itemsSnapshot; }
    public void setItemsSnapshot(String itemsSnapshot) { this.itemsSnapshot = itemsSnapshot; }
    public String getAdjustedByType() { return adjustedByType; }
    public void setAdjustedByType(String adjustedByType) { this.adjustedByType = adjustedByType; }
    public Long getAdjustedById() { return adjustedById; }
    public void setAdjustedById(Long adjustedById) { this.adjustedById = adjustedById; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
