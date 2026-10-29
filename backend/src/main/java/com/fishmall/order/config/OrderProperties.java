package com.fishmall.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Component
@Validated
@ConfigurationProperties(prefix = "order")
public class OrderProperties {
    private int paymentTimeoutMinutes = 60;
    @Min(1) @Max(525600) private int confirmTimeoutMinutes = 1440;
    private boolean timeoutCloseEnabled = true;
    @Min(1000) private long timeoutCloseDelayMs = 30000;
    @Min(1) @Max(100) private int timeoutCloseBatchSize = 50;
    @Min(1) @Max(100) private int timeoutCloseMaxBatches = 10;
    @Min(1) private long timeoutCloseBudgetMs = 20000;

    public int getConfirmTimeoutMinutes() { return confirmTimeoutMinutes; }
    public void setConfirmTimeoutMinutes(int v) { confirmTimeoutMinutes = v; }
    public boolean isTimeoutCloseEnabled() { return timeoutCloseEnabled; }
    public void setTimeoutCloseEnabled(boolean v) { timeoutCloseEnabled = v; }
    public long getTimeoutCloseDelayMs() { return timeoutCloseDelayMs; }
    public void setTimeoutCloseDelayMs(long v) { timeoutCloseDelayMs = v; }
    public int getTimeoutCloseBatchSize() { return timeoutCloseBatchSize; }
    public void setTimeoutCloseBatchSize(int v) { timeoutCloseBatchSize = v; }
    public int getTimeoutCloseMaxBatches() { return timeoutCloseMaxBatches; }
    public void setTimeoutCloseMaxBatches(int v) { timeoutCloseMaxBatches = v; }
    public long getTimeoutCloseBudgetMs() { return timeoutCloseBudgetMs; }
    public void setTimeoutCloseBudgetMs(long v) { timeoutCloseBudgetMs = v; }

    public int getPaymentTimeoutMinutes() {
        return paymentTimeoutMinutes;
    }

    public void setPaymentTimeoutMinutes(int paymentTimeoutMinutes) {
        this.paymentTimeoutMinutes = paymentTimeoutMinutes;
    }
}
