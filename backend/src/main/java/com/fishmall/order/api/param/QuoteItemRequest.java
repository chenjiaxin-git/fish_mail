package com.fishmall.order.api.param;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record QuoteItemRequest(
        @NotNull Long orderItemId,
        @NotNull @DecimalMin(value = "0.001") @Digits(integer = 9, fraction = 3) BigDecimal actualQuantity,
        @NotNull @PositiveOrZero Long finalAmountCent
) {
}
