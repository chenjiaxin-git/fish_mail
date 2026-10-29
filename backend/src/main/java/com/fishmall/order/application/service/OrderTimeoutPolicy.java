package com.fishmall.order.application.service;

import com.fishmall.order.config.OrderProperties;
import com.fishmall.order.domain.*;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Component
public class OrderTimeoutPolicy {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final OrderProperties properties;
    private final Clock clock;
    public OrderTimeoutPolicy(OrderProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }
    public LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZONE); }
    public LocalDateTime createdCutoff(LocalDateTime now) { return now.minusMinutes(properties.getConfirmTimeoutMinutes()); }
    public LocalDateTime deadline(SalesOrder o) {
        return switch (o.status()) {
            case CREATED -> o.getCreatedAt() == null ? null : o.getCreatedAt().plusMinutes(properties.getConfirmTimeoutMinutes());
            case QUOTED, PENDING_PAYMENT -> o.getPayDeadlineAt();
            default -> null;
        };
    }
    public boolean due(SalesOrder o) {
        LocalDateTime deadline = deadline(o);
        return deadline != null && !now().isBefore(deadline);
    }
    public boolean matches(SalesOrder o, TimeoutObservation seen) {
        return o.status() == seen.status() && Objects.equals(o.getVersion(), seen.version())
                && Objects.equals(deadline(o), seen.deadline()) && due(o);
    }
    public long retrySeconds(int attempt) { return Math.min(1800L, 30L << Math.min(6, Math.max(0, attempt - 1))); }
}
