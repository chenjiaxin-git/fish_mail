package com.fishmall.order.api.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundConfirmRequest(@NotBlank @Size(max = 100) String refundReference) {
}
