package com.fishmall.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "order")
public class OrderProperties {
    private int paymentTimeoutMinutes = 60;

    public int getPaymentTimeoutMinutes() {
        return paymentTimeoutMinutes;
    }

    public void setPaymentTimeoutMinutes(int paymentTimeoutMinutes) {
        this.paymentTimeoutMinutes = paymentTimeoutMinutes;
    }
}
