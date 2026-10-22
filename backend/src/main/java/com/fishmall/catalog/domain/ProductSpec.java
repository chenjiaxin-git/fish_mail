package com.fishmall.catalog.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductSpec {
    private Long id;
    private Long productId;
    private String name;
    private String quantityKind;
    private String unit;
    private Long priceCent;
    private BigDecimal minQuantity;
    private BigDecimal stepQuantity;
    private BigDecimal physicalStockQty;
    private BigDecimal reservedQty;
    private BigDecimal warningQty;
    private String status;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getQuantityKind() { return quantityKind; }
    public void setQuantityKind(String quantityKind) { this.quantityKind = quantityKind; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Long getPriceCent() { return priceCent; }
    public void setPriceCent(Long priceCent) { this.priceCent = priceCent; }
    public BigDecimal getMinQuantity() { return minQuantity; }
    public void setMinQuantity(BigDecimal minQuantity) { this.minQuantity = minQuantity; }
    public BigDecimal getStepQuantity() { return stepQuantity; }
    public void setStepQuantity(BigDecimal stepQuantity) { this.stepQuantity = stepQuantity; }
    public BigDecimal getPhysicalStockQty() { return physicalStockQty; }
    public void setPhysicalStockQty(BigDecimal physicalStockQty) { this.physicalStockQty = physicalStockQty; }
    public BigDecimal getReservedQty() { return reservedQty; }
    public void setReservedQty(BigDecimal reservedQty) { this.reservedQty = reservedQty; }
    public BigDecimal getWarningQty() { return warningQty; }
    public void setWarningQty(BigDecimal warningQty) { this.warningQty = warningQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
