package com.fishmall.catalog.api.param;

import jakarta.validation.constraints.*;

public record BannerRequest(@NotBlank(message="标题不能为空") @Size(max=100,message="标题最多100字") String title,
        @NotBlank(message="请上传图片") @Size(max=500,message="图片地址过长") String imageUrl,
        @NotNull @Pattern(regexp="NONE|PRODUCT|CATEGORY",message="跳转类型不合法") String jumpType,
        @Positive(message="目标编号必须为正整数") Long jumpTargetId,
        @NotNull @PositiveOrZero(message="排序必须为非负整数") Integer sort,
        @NotNull @Pattern(regexp="ACTIVE|DISABLED",message="轮播状态不合法") String status) { }
