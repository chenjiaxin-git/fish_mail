package com.fishmall.cart.api.param;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CartAddRequest(
        @NotNull Long productSpecId,
        @NotNull @Positive BigDecimal quantity
) {
}
