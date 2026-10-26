package com.fishmall.order.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SalesOrderItem {
    private Long id;
    private Long orderId;
    private Long productId;
    private Long productSpecId;
    private String productName;
    private String specName;
    private String quantityKind;
    private String unit;
    private Long unitPriceCent;
    private BigDecimal estimatedQuantity;
    private BigDecimal actualQuantity;
    private Long estimatedAmountCent;
    private Long finalAmountCent;
    private String productImageUrl;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getProductSpecId() { return productSpecId; }
    public void setProductSpecId(Long productSpecId) { this.productSpecId = productSpecId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSpecName() { return specName; }
    public void setSpecName(String specName) { this.specName = specName; }
    public String getQuantityKind() { return quantityKind; }
    public void setQuantityKind(String quantityKind) { this.quantityKind = quantityKind; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Long getUnitPriceCent() { return unitPriceCent; }
    public void setUnitPriceCent(Long unitPriceCent) { this.unitPriceCent = unitPriceCent; }
    public BigDecimal getEstimatedQuantity() { return estimatedQuantity; }
    public void setEstimatedQuantity(BigDecimal estimatedQuantity) { this.estimatedQuantity = estimatedQuantity; }
    public BigDecimal getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(BigDecimal actualQuantity) { this.actualQuantity = actualQuantity; }
    public Long getEstimatedAmountCent() { return estimatedAmountCent; }
    public void setEstimatedAmountCent(Long estimatedAmountCent) { this.estimatedAmountCent = estimatedAmountCent; }
    public Long getFinalAmountCent() { return finalAmountCent; }
    public void setFinalAmountCent(Long finalAmountCent) { this.finalAmountCent = finalAmountCent; }
    public String getProductImageUrl() { return productImageUrl; }
    public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
