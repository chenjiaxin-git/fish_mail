package com.fishmall.catalog.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductStockLedger {
    private Long id;
    private Long productSpecId;
    private String changeType;
    private BigDecimal quantity;
    private BigDecimal physicalBefore;
    private BigDecimal physicalAfter;
    private BigDecimal reservedBefore;
    private BigDecimal reservedAfter;
    private String reason;
    private String operatorType;
    private Long operatorId;
    private String bizKey;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductSpecId() { return productSpecId; }
    public void setProductSpecId(Long productSpecId) { this.productSpecId = productSpecId; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getPhysicalBefore() { return physicalBefore; }
    public void setPhysicalBefore(BigDecimal physicalBefore) { this.physicalBefore = physicalBefore; }
    public BigDecimal getPhysicalAfter() { return physicalAfter; }
    public void setPhysicalAfter(BigDecimal physicalAfter) { this.physicalAfter = physicalAfter; }
    public BigDecimal getReservedBefore() { return reservedBefore; }
    public void setReservedBefore(BigDecimal reservedBefore) { this.reservedBefore = reservedBefore; }
    public BigDecimal getReservedAfter() { return reservedAfter; }
    public void setReservedAfter(BigDecimal reservedAfter) { this.reservedAfter = reservedAfter; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperatorType() { return operatorType; }
    public void setOperatorType(String operatorType) { this.operatorType = operatorType; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getBizKey() { return bizKey; }
    public void setBizKey(String bizKey) { this.bizKey = bizKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
