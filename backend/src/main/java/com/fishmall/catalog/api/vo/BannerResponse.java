package com.fishmall.catalog.api.vo;

public record BannerResponse(
        Long id,
        String title,
        String imageUrl,
        String jumpType,
        Long jumpTargetId,
        Integer sort
) {
}
