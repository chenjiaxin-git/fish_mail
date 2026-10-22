package com.fishmall.catalog.api.param;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockAdjustRequest(
        @NotNull(message = "调整后的库存不能为空") @DecimalMin(value = "0", message = "库存不能小于0") @Digits(integer = 9, fraction = 3, message = "库存最多三位小数") BigDecimal targetQuantity,
        @NotBlank(message = "调整原因不能为空") @Size(max = 200, message = "调整原因不能超过200个字符") String reason
) {
}
