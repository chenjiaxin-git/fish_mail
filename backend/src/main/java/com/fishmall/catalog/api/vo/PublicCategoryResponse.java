package com.fishmall.catalog.api.vo;

public record PublicCategoryResponse(
        Long id,
        String name,
        Integer sort
) {
}
