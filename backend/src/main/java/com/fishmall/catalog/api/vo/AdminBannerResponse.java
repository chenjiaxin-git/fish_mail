package com.fishmall.catalog.api.vo;

import java.time.LocalDateTime;

public record AdminBannerResponse(Long id,String title,String imageUrl,String jumpType,Long jumpTargetId,
        Integer sort,String status,boolean targetAvailable,String targetName,LocalDateTime createdAt,LocalDateTime updatedAt) { }
