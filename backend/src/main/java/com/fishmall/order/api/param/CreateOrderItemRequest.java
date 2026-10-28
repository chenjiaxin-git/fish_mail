package com.fishmall.order.api.param;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOrderItemRequest(
        @NotNull Long productSpecId,
        @NotNull BigDecimal quantity
) {
}
