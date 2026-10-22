package com.fishmall.catalog.api.param;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductRequest(
        @NotNull(message = "分类不能为空") Long categoryId,
        @NotBlank(message = "商品名称不能为空") @Size(max = 100, message = "商品名称不能超过100个字符") String name,
        @Size(max = 150, message = "副标题不能超过150个字符") String subtitle,
        @Size(max = 5000, message = "商品描述不能超过5000个字符") String description,
        @Size(max = 500, message = "图片地址不能超过500个字符") String mainImageUrl,
        @NotBlank(message = "商品状态不能为空") @Pattern(regexp = "ACTIVE|OFF_SHELF", message = "商品状态不合法") String status,
        @NotEmpty(message = "至少需要一个规格") @Size(max = 20, message = "规格不能超过20个") @Valid List<ProductSpecRequest> specs
) {
}
