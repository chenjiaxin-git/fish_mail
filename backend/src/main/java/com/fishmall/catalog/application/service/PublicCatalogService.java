package com.fishmall.catalog.application.service;

import com.fishmall.catalog.api.vo.BannerResponse;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.api.vo.PublicCategoryResponse;
import com.fishmall.catalog.api.vo.PublicProductListResponse;
import com.fishmall.catalog.api.vo.PublicProductSpecResponse;
import com.fishmall.catalog.domain.Product;
import com.fishmall.catalog.domain.ProductCategory;
import com.fishmall.catalog.domain.ProductSpec;
import com.fishmall.catalog.domain.ShopBanner;
import com.fishmall.catalog.repository.ProductCategoryMapper;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.catalog.repository.ShopBannerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PublicCatalogService {
    private static final String PRODUCT_STATUS_ACTIVE = "ACTIVE";

    private final ShopBannerMapper bannerMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ProductSpecMapper specMapper;

    public PublicCatalogService(
            ShopBannerMapper bannerMapper,
            ProductCategoryMapper categoryMapper,
            ProductMapper productMapper,
            ProductSpecMapper specMapper
    ) {
        this.bannerMapper = bannerMapper;
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.specMapper = specMapper;
    }

    @Transactional(readOnly = true)
    public List<BannerResponse> banners() {
        return bannerMapper.findAllActive().stream().map(this::toBannerResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PublicCategoryResponse> categories() {
        return categoryMapper.findAllActive().stream()
                .map(this::toCategoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicProductListResponse> products(String keyword, Long categoryId, int page, int pageSize) {
        validatePage(page, pageSize);
        if (categoryId != null && categoryId < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分类参数不合法");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        long rawOffset = (long) (page - 1) * pageSize;
        if (rawOffset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
        int offset = (int) rawOffset;
        List<Product> products = productMapper.selectPage(
                normalizedKeyword, categoryId, PRODUCT_STATUS_ACTIVE, offset, pageSize);
        long total = productMapper.count(normalizedKeyword, categoryId, PRODUCT_STATUS_ACTIVE);
        if (products.isEmpty()) {
            return new PageResponse<>(total, page, pageSize, Collections.emptyList());
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, List<ProductSpec>> specsByProduct = specMapper.findActiveByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(ProductSpec::getProductId));
        Map<Long, ProductCategory> categoriesById = categoryMapper.findAllByIds(
                        products.stream().map(Product::getCategoryId).distinct().toList()).stream()
                .collect(Collectors.toMap(ProductCategory::getId, Function.identity()));

        List<PublicProductListResponse> items = new ArrayList<>(products.size());
        for (Product product : products) {
            items.add(toProductResponse(product, specsByProduct.getOrDefault(product.getId(), Collections.emptyList()),
                    categoriesById.get(product.getCategoryId())));
        }
        return new PageResponse<>(total, page, pageSize, items);
    }

    private BannerResponse toBannerResponse(ShopBanner banner) {
        return new BannerResponse(banner.getId(), banner.getTitle(), banner.getImageUrl(), banner.getJumpType(),
                banner.getJumpTargetId(), banner.getSort());
    }

    private PublicCategoryResponse toCategoryResponse(ProductCategory category) {
        return new PublicCategoryResponse(category.getId(), category.getName(), category.getSort());
    }

    private PublicProductListResponse toProductResponse(
            Product product,
            List<ProductSpec> specs,
            ProductCategory category
    ) {
        Long minPriceCent = null;
        Long maxPriceCent = null;
        BigDecimal totalAvailableQty = BigDecimal.ZERO;
        List<PublicProductSpecResponse> specResponses = new ArrayList<>(specs.size());
        for (ProductSpec spec : specs) {
            minPriceCent = minPriceCent == null ? spec.getPriceCent() : Math.min(minPriceCent, spec.getPriceCent());
            maxPriceCent = maxPriceCent == null ? spec.getPriceCent() : Math.max(maxPriceCent, spec.getPriceCent());
            BigDecimal availableQty = spec.getPhysicalStockQty().subtract(spec.getReservedQty());
            totalAvailableQty = totalAvailableQty.add(availableQty);
            specResponses.add(new PublicProductSpecResponse(spec.getId(), spec.getProductId(), spec.getName(),
                    spec.getQuantityKind(), spec.getUnit(), spec.getPriceCent(), spec.getMinQuantity(),
                    spec.getStepQuantity(), availableQty));
        }
        return new PublicProductListResponse(product.getId(), product.getCategoryId(),
                category == null ? null : category.getName(), product.getName(), product.getSubtitle(),
                product.getMainImageUrl(), minPriceCent, maxPriceCent, totalAvailableQty, specResponses);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) return null;
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品名称搜索词不能超过100个字符");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
    }
}
