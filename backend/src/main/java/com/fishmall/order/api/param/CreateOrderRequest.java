package com.fishmall.order.api.param;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotNull Long addressId,
        @Size(max = 500) String remark,
        @NotEmpty @Size(max = 50) List<@Valid CreateOrderItemRequest> items,
        @Size(max = 50) List<Long> cartItemIds,
        @NotBlank @Size(max = 64) String idempotencyKey
) {
}
