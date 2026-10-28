package com.fishmall.order.domain;

import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class OrderStateMachine {
    private static final Map<OrderAction, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderAction.class);

    static {
        ALLOWED.put(OrderAction.QUOTE, EnumSet.of(OrderStatus.CREATED, OrderStatus.QUOTED));
        ALLOWED.put(OrderAction.DELIVER, EnumSet.of(OrderStatus.PAID_WAIT_DELIVERY));
        ALLOWED.put(OrderAction.COMPLETE, EnumSet.of(OrderStatus.DELIVERING));
        ALLOWED.put(OrderAction.CANCEL, EnumSet.of(OrderStatus.CREATED, OrderStatus.QUOTED,
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID_WAIT_DELIVERY));
    }

    private OrderStateMachine() {
    }

    public static void validate(OrderStatus currentStatus, OrderAction action) {
        if (currentStatus == null || action == null || !ALLOWED.get(action).contains(currentStatus)) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }
    }

    public static OrderStatus validateCreate() {
        return OrderStatus.CREATED;
    }

    public static boolean isPaid(OrderStatus status) {
        return status == OrderStatus.PAID_WAIT_DELIVERY || status == OrderStatus.DELIVERING
                || status == OrderStatus.COMPLETED;
    }
}
