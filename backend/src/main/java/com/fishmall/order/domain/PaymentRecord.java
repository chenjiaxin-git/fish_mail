package com.fishmall.order.domain;

import java.time.LocalDateTime;

public class PaymentRecord {
    private Integer quoteVersion;
    private String appid;
    private String mchid;
    private String payerOpenid;
    private String prepayId;
    private LocalDateTime expiresAt;
    private String phase;
    private String operationToken;
    private LocalDateTime operationUntil;
    private LocalDateTime syncAfter;
    public Integer getQuoteVersion() { return quoteVersion; }
    public void setQuoteVersion(Integer v) { quoteVersion = v; }
    public String getAppid() { return appid; }
    public void setAppid(String v) { appid = v; }
    public String getMchid() { return mchid; }
    public void setMchid(String v) { mchid = v; }
    public String getPayerOpenid() { return payerOpenid; }
    public void setPayerOpenid(String v) { payerOpenid = v; }
    public String getPrepayId() { return prepayId; }
    public void setPrepayId(String v) { prepayId = v; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime v) { expiresAt = v; }
    public String getPhase() { return phase; }
    public void setPhase(String v) { phase = v; }
    public String getOperationToken() { return operationToken; }
    public void setOperationToken(String v) { operationToken = v; }
    public LocalDateTime getOperationUntil() { return operationUntil; }
    public void setOperationUntil(LocalDateTime v) { operationUntil = v; }
    public LocalDateTime getSyncAfter() { return syncAfter; }
    public void setSyncAfter(LocalDateTime v) { syncAfter = v; }
    private Long id;
    private Long orderId;
    private String outTradeNo;
    private String transactionId;
    private String state;
    private Long amountCent;
    private String tradeState;
    private String notifyRaw;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Long getAmountCent() { return amountCent; }
    public void setAmountCent(Long amountCent) { this.amountCent = amountCent; }
    public String getTradeState() { return tradeState; }
    public void setTradeState(String tradeState) { this.tradeState = tradeState; }
    public String getNotifyRaw() { return notifyRaw; }
    public void setNotifyRaw(String notifyRaw) { this.notifyRaw = notifyRaw; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
