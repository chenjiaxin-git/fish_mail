package com.fishmall.order.api.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(@NotBlank(message = "取消原因不能为空") @Size(max = 500, message = "取消原因不能超过500字") String reason) {
}
