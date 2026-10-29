package com.fishmall.order.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="order", name="timeout-close-enabled", havingValue="true", matchIfMissing=true)
public class OrderTimeoutJob {
    private final OrderTimeoutService service;
    public OrderTimeoutJob(OrderTimeoutService service) { this.service = service; }
    @Scheduled(scheduler="orderTimeoutScheduler", fixedDelayString="${order.timeout-close-delay-ms:30000}",
            initialDelayString="${order.timeout-close-delay-ms:30000}")
    public void run() { service.scan(); }
}
