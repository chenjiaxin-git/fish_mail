package com.fishmall.order.domain;

public enum OrderStatus {
    CREATED,
    QUOTED,
    PENDING_PAYMENT,
    PAID_WAIT_DELIVERY,
    DELIVERING,
    COMPLETED,
    CANCELLED,
    CLOSED
}
