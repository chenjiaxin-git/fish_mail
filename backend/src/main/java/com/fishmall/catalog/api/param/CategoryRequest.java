package com.fishmall.catalog.api.param;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "分类名称不能为空") @Size(max = 50, message = "分类名称不能超过50个字符") String name,
        @NotNull(message = "排序不能为空") @Min(value = 0, message = "排序不能小于0") @Max(value = 9999, message = "排序不能大于9999") Integer sort,
        @NotBlank(message = "分类状态不能为空") @Pattern(regexp = "ACTIVE|DISABLED", message = "分类状态不合法") String status
) {
}
