package com.fishmall.cart.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CartItem {
    private Long id;
    private Long wxUserId;
    private Long productSpecId;
    private BigDecimal quantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWxUserId() { return wxUserId; }
    public void setWxUserId(Long wxUserId) { this.wxUserId = wxUserId; }
    public Long getProductSpecId() { return productSpecId; }
    public void setProductSpecId(Long productSpecId) { this.productSpecId = productSpecId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
