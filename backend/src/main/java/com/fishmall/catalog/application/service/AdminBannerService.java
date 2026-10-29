package com.fishmall.catalog.application.service;

import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.param.BannerRequest;
import com.fishmall.catalog.api.vo.AdminBannerResponse;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.config.ProductStorageProperties;
import com.fishmall.catalog.domain.ShopBanner;
import com.fishmall.catalog.repository.ProductCategoryMapper;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.catalog.repository.ShopBannerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class AdminBannerService {
    private final ShopBannerMapper banners;
    private final ProductMapper products;
    private final ProductCategoryMapper categories;
    private final AdminOperationLogMapper audit;
    private final Pattern imagePattern;

    public AdminBannerService(ShopBannerMapper banners, ProductMapper products, ProductCategoryMapper categories,
                              AdminOperationLogMapper audit, ProductStorageProperties storage) {
        this.banners = banners;
        this.products = products;
        this.categories = categories;
        this.audit = audit;
        imagePattern = Pattern.compile(Pattern.quote(storage.imageBaseUrl()) + "/[a-fA-F0-9-]{36}\\.(jpg|jpeg|png|webp)");
    }

    @Transactional
    public PageResponse<AdminBannerResponse> list(String status, int page, int size,
                                                 AdminPrincipal principal, String trace) {
        long offset = ((long) page - 1) * size;
        if (page < 1 || size < 1 || size > 100 || offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
        if (status != null && status.isBlank()) status = null;
        if (status != null && !status.equals("ACTIVE") && !status.equals("DISABLED")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "轮播状态不合法");
        }
        var result = new PageResponse<>(banners.count(status), page, size, banners.page(status, (int) offset, size));
        log(principal, "LIST", null, trace);
        return result;
    }

    @Transactional
    public AdminBannerResponse detail(Long id, AdminPrincipal principal, String trace) {
        var result = require(id);
        log(principal, "DETAIL", id, trace);
        return result;
    }

    @Transactional
    public AdminBannerResponse create(BannerRequest request, AdminPrincipal principal, String trace) {
        ShopBanner banner = build(request);
        banners.insert(banner);
        log(principal, "CREATE", banner.getId(), trace);
        return require(banner.getId());
    }

    @Transactional
    public AdminBannerResponse update(Long id, BannerRequest request, AdminPrincipal principal, String trace) {
        require(id);
        ShopBanner banner = build(request);
        banner.setId(id);
        if (banners.update(banner) != 1) throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        log(principal, "UPDATE", id, trace);
        return require(id);
    }

    @Transactional
    public void delete(Long id, AdminPrincipal principal, String trace) {
        require(id);
        if (banners.delete(id) != 1) throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        log(principal, "DELETE", id, trace);
    }

    private ShopBanner build(BannerRequest request) {
        if (!imagePattern.matcher(request.imageUrl()).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请使用图片上传接口返回的地址");
        }
        if (!"NONE".equals(request.jumpType())) {
            if (request.jumpTargetId() == null) throw new BusinessException(ErrorCode.BANNER_TARGET_INVALID);
            boolean active;
            if ("PRODUCT".equals(request.jumpType())) {
                active = "ACTIVE".equals(products.findActiveById(request.jumpTargetId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.BANNER_TARGET_INVALID)).getStatus());
            } else {
                active = "ACTIVE".equals(categories.findActiveById(request.jumpTargetId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.BANNER_TARGET_INVALID)).getStatus());
            }
            if ("ACTIVE".equals(request.status()) && !active) {
                throw new BusinessException(ErrorCode.BANNER_TARGET_INVALID);
            }
        }
        var banner = new ShopBanner();
        banner.setTitle(request.title().trim());
        banner.setImageUrl(request.imageUrl());
        banner.setJumpType(request.jumpType());
        banner.setJumpTargetId("NONE".equals(request.jumpType()) ? null : request.jumpTargetId());
        banner.setSort(request.sort());
        banner.setStatus(request.status());
        return banner;
    }

    private AdminBannerResponse require(Long id) {
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return banners.detail(id).orElseThrow(() -> new BusinessException(ErrorCode.BANNER_NOT_FOUND));
    }

    private void log(AdminPrincipal principal, String action, Long id, String trace) {
        audit.insert(principal.adminId(), principal.username(), "BANNER", action, id == null ? null : id.toString(),
                "SUCCESS", null, trace);
    }
}
