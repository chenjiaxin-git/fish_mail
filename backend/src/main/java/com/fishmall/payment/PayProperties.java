package com.fishmall.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat-pay")
public record PayProperties(boolean enabled, String appId, String mchId, String merchantSerialNo,
                            String privateKeyPath, String publicKeyId, String publicKeyPath,
                            String apiV3Key, String notifyUrl, int timeoutMs) {
    public PayProperties { if (timeoutMs <= 0) timeoutMs = 5000; }
    @Override public String toString() { return "PayProperties[enabled=" + enabled + ", secrets=redacted]"; }
}
