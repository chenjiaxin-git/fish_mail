package com.fishmall.order.api.param;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuoteRequest(
        @NotNull @PositiveOrZero Integer quoteVersion,
        @NotEmpty @Size(max = 50) List<@Valid QuoteItemRequest> items,
        @NotNull @PositiveOrZero Long finalAmountCent,
        @NotBlank @Size(max = 500) String reason
) {
}
