package com.fishmall.catalog.api.param;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductSpecRequest(
        Long id,
        @NotBlank(message = "规格名称不能为空") @Size(max = 50, message = "规格名称不能超过50个字符") String name,
        @NotBlank(message = "计价方式不能为空") @Pattern(regexp = "PIECE|WEIGHT", message = "计价方式不合法") String quantityKind,
        @NotBlank(message = "单位不能为空") @Size(max = 20, message = "单位不能超过20个字符") String unit,
        @NotNull(message = "单价不能为空") @Min(value = 1, message = "单价必须大于0") Long priceCent,
        @NotNull(message = "最小购买量不能为空") @DecimalMin(value = "0", inclusive = false, message = "最小购买量必须大于0") @Digits(integer = 9, fraction = 3, message = "最小购买量最多三位小数") BigDecimal minQuantity,
        @NotNull(message = "步进值不能为空") @DecimalMin(value = "0", inclusive = false, message = "步进值必须大于0") @Digits(integer = 9, fraction = 3, message = "步进值最多三位小数") BigDecimal stepQuantity,
        @NotNull(message = "库存不能为空") @DecimalMin(value = "0", message = "库存不能小于0") @Digits(integer = 9, fraction = 3, message = "库存最多三位小数") BigDecimal physicalStockQty,
        @NotNull(message = "预警量不能为空") @DecimalMin(value = "0", message = "预警量不能小于0") @Digits(integer = 9, fraction = 3, message = "预警量最多三位小数") BigDecimal warningQty,
        @NotBlank(message = "规格状态不能为空") @Pattern(regexp = "ACTIVE|DISABLED", message = "规格状态不合法") String status
) {
}
