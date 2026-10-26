package com.fishmall.catalog.domain;

import java.time.LocalDateTime;

public class ShopBanner {
    private Long id;
    private String title;
    private String imageUrl;
    private String jumpType;
    private Long jumpTargetId;
    private Integer sort;
    private String status;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getJumpType() { return jumpType; }
    public void setJumpType(String jumpType) { this.jumpType = jumpType; }
    public Long getJumpTargetId() { return jumpTargetId; }
    public void setJumpTargetId(Long jumpTargetId) { this.jumpTargetId = jumpTargetId; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
