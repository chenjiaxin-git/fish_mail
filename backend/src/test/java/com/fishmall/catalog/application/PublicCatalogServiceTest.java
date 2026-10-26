package com.fishmall.catalog.application;

import com.fishmall.catalog.api.vo.BannerResponse;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.api.vo.PublicCategoryResponse;
import com.fishmall.catalog.api.vo.PublicProductListResponse;
import com.fishmall.catalog.application.service.PublicCatalogService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCatalogServiceTest {
    @Mock
    private ShopBannerMapper bannerMapper;
    @Mock
    private ProductCategoryMapper categoryMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSpecMapper specMapper;

    private PublicCatalogService service;

    @BeforeEach
    void setUp() {
        service = new PublicCatalogService(bannerMapper, categoryMapper, productMapper, specMapper);
    }

    @Test
    void bannersReturnPublicJumpFields() {
        ShopBanner banner = new ShopBanner();
        banner.setId(1L);
        banner.setTitle("今日鲜鱼");
        banner.setImageUrl("/uploads/banner.jpg");
        banner.setJumpType("CATEGORY");
        banner.setJumpTargetId(2L);
        banner.setSort(1);
        when(bannerMapper.findAllActive()).thenReturn(List.of(banner));

        List<BannerResponse> result = service.banners();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).jumpType()).isEqualTo("CATEGORY");
        assertThat(result.get(0).jumpTargetId()).isEqualTo(2L);
    }

    @Test
    void categoriesReturnActivePublicFields() {
        ProductCategory category = new ProductCategory();
        category.setId(1L);
        category.setName("淡水鱼");
        category.setSort(3);
        when(categoryMapper.findAllActive()).thenReturn(List.of(category));

        List<PublicCategoryResponse> result = service.categories();

        assertThat(result).containsExactly(new PublicCategoryResponse(1L, "淡水鱼", 3));
    }

    @Test
    void productsAggregatePricesStockAndSafeSpecs() {
        Product product = new Product();
        product.setId(10L);
        product.setCategoryId(1L);
        product.setName("草鱼");
        product.setSubtitle("鲜活");
        product.setMainImageUrl("/uploads/products/10.jpg");
        ProductSpec first = spec(20L, 10L, 3000L, new BigDecimal("3.500"), new BigDecimal("1.500"));
        ProductSpec second = spec(21L, 10L, 4500L, new BigDecimal("2.000"), BigDecimal.ZERO);
        ProductCategory category = new ProductCategory();
        category.setId(1L);
        category.setName("淡水鱼");

        when(productMapper.selectPage(isNull(), eq(1L), eq("ACTIVE"), eq(0), eq(20)))
                .thenReturn(List.of(product));
        when(productMapper.count(isNull(), eq(1L), eq("ACTIVE"))).thenReturn(1L);
        when(specMapper.findActiveByProductIds(List.of(10L))).thenReturn(List.of(first, second));
        when(categoryMapper.findAllByIds(List.of(1L))).thenReturn(List.of(category));

        PageResponse<PublicProductListResponse> result = service.products(null, 1L, 1, 20);

        assertThat(result.total()).isEqualTo(1L);
        PublicProductListResponse item = result.items().get(0);
        assertThat(item.minPriceCent()).isEqualTo(3000L);
        assertThat(item.maxPriceCent()).isEqualTo(4500L);
        assertThat(item.totalAvailableQty()).isEqualByComparingTo(new BigDecimal("4.000"));
        assertThat(item.specs()).hasSize(2);
        assertThat(item.specs().get(0).availableQty()).isEqualByComparingTo(new BigDecimal("2.000"));
    }

    @Test
    void productsRejectInvalidPageAndKeyword() {
        assertThatThrownBy(() -> service.products("x".repeat(101), null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.products(null, 0L, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.products(null, null, 0, 101))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    private ProductSpec spec(Long id, Long productId, Long priceCent, BigDecimal physical, BigDecimal reserved) {
        ProductSpec spec = new ProductSpec();
        spec.setId(id);
        spec.setProductId(productId);
        spec.setName("规格");
        spec.setQuantityKind("WEIGHT");
        spec.setUnit("kg");
        spec.setPriceCent(priceCent);
        spec.setMinQuantity(BigDecimal.ONE);
        spec.setStepQuantity(new BigDecimal("0.500"));
        spec.setPhysicalStockQty(physical);
        spec.setReservedQty(reserved);
        return spec;
    }
}
