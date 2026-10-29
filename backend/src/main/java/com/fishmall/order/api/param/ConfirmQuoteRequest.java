package com.fishmall.order.api.param;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmQuoteRequest(@NotNull(message = "报价版本不能为空") @Positive(message = "报价版本必须为正整数") Integer quoteVersion) {
}
