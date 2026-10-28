package com.fishmall.catalog.api.vo;

import java.math.BigDecimal;
import java.util.List;

public record PublicProductDetailResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String subtitle,
        String description,
        String mainImageUrl,
        Long minPriceCent,
        Long maxPriceCent,
        BigDecimal totalAvailableQty,
        List<PublicProductSpecResponse> specs
) {
}
