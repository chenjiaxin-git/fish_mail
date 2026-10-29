package com.fishmall.order.domain;

import java.time.LocalDateTime;

/** 不跨事务传递可变订单实体；所有写操作重新核验本快照。 */
public record TimeoutObservation(Long id, String orderNo, Long version, OrderStatus status, LocalDateTime deadline) { }
