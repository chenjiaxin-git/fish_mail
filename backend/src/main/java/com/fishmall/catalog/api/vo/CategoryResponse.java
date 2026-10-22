package com.fishmall.catalog.api.vo;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        Integer sort,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
