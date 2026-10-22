package com.fishmall.catalog.application;

import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.param.ProductRequest;
import com.fishmall.catalog.api.param.ProductSpecRequest;
import com.fishmall.catalog.api.param.StockAdjustRequest;
import com.fishmall.catalog.api.vo.ProductDetailResponse;
import com.fishmall.catalog.api.vo.StockAdjustResponse;
import com.fishmall.catalog.application.service.AdminProductService;
import com.fishmall.catalog.domain.Product;
import com.fishmall.catalog.domain.ProductCategory;
import com.fishmall.catalog.domain.ProductSpec;
import com.fishmall.catalog.domain.ProductStockLedger;
import com.fishmall.catalog.repository.ProductCategoryMapper;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.catalog.repository.ProductStockLedgerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductCategoryMapper categoryMapper;
    @Mock
    private ProductSpecMapper specMapper;
    @Mock
    private ProductStockLedgerMapper stockLedgerMapper;
    @Mock
    private AdminOperationLogMapper operationLogMapper;

    private AdminProductService service;

    @BeforeEach
    void setUp() {
        service = new AdminProductService(productMapper, categoryMapper, specMapper, stockLedgerMapper, operationLogMapper);
    }

    @Test
    void createWritesInitialStockLedgerForNewSpec() {
        when(categoryMapper.findActiveById(1L)).thenReturn(Optional.of(activeCategory()));
        when(productMapper.insert(any(Product.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Product.class).setId(10L);
            return 1;
        });
        when(specMapper.insert(any(ProductSpec.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ProductSpec.class).setId(20L);
            return 1;
        });
        when(stockLedgerMapper.insert(any(ProductStockLedger.class))).thenReturn(1);
        when(specMapper.findActiveByProductId(10L)).thenReturn(List.of(spec(20L, "按斤", BigDecimal.TEN)));

        ProductDetailResponse response = service.create(request(), principal(), "trace");

        assertThat(response.id()).isEqualTo(10L);
        verify(stockLedgerMapper).insert(org.mockito.ArgumentMatchers.argThat(
                ledger -> "INITIAL".equals(ledger.getChangeType()) && ledger.getBizKey().startsWith("INITIAL:20:")));
    }

    @Test
    void updateRejectsDeletingReservedSpec() {
        ProductSpec reserved = spec(20L, "按斤", BigDecimal.TEN);
        reserved.setReservedQty(new BigDecimal("1.000"));
        when(categoryMapper.findActiveById(1L)).thenReturn(Optional.of(activeCategory()));
        when(productMapper.findActiveById(10L)).thenReturn(Optional.of(product()));
        when(specMapper.findActiveByProductId(10L)).thenReturn(List.of(reserved));

        ProductRequest request = new ProductRequest(1L, "草鱼", "", "", "", "ACTIVE", List.of());
        assertThatThrownBy(() -> service.update(10L, request, principal(), "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_SPEC_IN_USE);
    }

    @Test
    void stockAdjustReturnsConflictWhenConditionalUpdateLosesRace() {
        ProductSpec spec = spec(20L, "按斤", BigDecimal.TEN);
        when(specMapper.findActiveByIdAndProductId(20L, 10L)).thenReturn(Optional.of(spec));
        when(specMapper.updatePhysicalStock(20L, BigDecimal.TEN, new BigDecimal("12.000"))).thenReturn(0);

        assertThatThrownBy(() -> service.adjustStock(10L, 20L, new StockAdjustRequest(new BigDecimal("12.000"), "盘点"), principal(), "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.STOCK_VERSION_CONFLICT);
    }

    @Test
    void stockAdjustWritesLedgerAndReturnsAvailableQuantity() {
        ProductSpec spec = spec(20L, "按斤", BigDecimal.TEN);
        spec.setReservedQty(new BigDecimal("2.000"));
        when(specMapper.findActiveByIdAndProductId(20L, 10L)).thenReturn(Optional.of(spec));
        when(specMapper.updatePhysicalStock(20L, BigDecimal.TEN, new BigDecimal("12.000"))).thenReturn(1);
        when(stockLedgerMapper.insert(any(ProductStockLedger.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ProductStockLedger.class).setId(30L);
            return 1;
        });

        StockAdjustResponse response = service.adjustStock(10L, 20L,
                new StockAdjustRequest(new BigDecimal("12.000"), "补货"), principal(), "trace");

        assertThat(response.availableQuantity()).isEqualByComparingTo(new BigDecimal("10.000"));
        verify(stockLedgerMapper).insert(any(ProductStockLedger.class));
    }

    private ProductRequest request() {
        ProductSpecRequest spec = new ProductSpecRequest(null, "按斤", "WEIGHT", "kg", 3000L,
                BigDecimal.ONE, new BigDecimal("0.500"), BigDecimal.TEN, BigDecimal.ONE, "ACTIVE");
        return new ProductRequest(1L, "草鱼", "鲜活", "适合清蒸", "/uploads/products/a.jpg", "ACTIVE", List.of(spec));
    }

    private Product product() {
        Product product = new Product();
        product.setId(10L);
        product.setCategoryId(1L);
        product.setName("草鱼");
        product.setStatus("ACTIVE");
        return product;
    }

    private ProductCategory activeCategory() {
        ProductCategory category = new ProductCategory();
        category.setId(1L);
        category.setName("淡水鱼");
        category.setStatus("ACTIVE");
        return category;
    }

    private ProductSpec spec(Long id, String name, BigDecimal physicalStockQty) {
        ProductSpec spec = new ProductSpec();
        spec.setId(id);
        spec.setProductId(10L);
        spec.setName(name);
        spec.setQuantityKind("WEIGHT");
        spec.setUnit("kg");
        spec.setPriceCent(3000L);
        spec.setMinQuantity(BigDecimal.ONE);
        spec.setStepQuantity(new BigDecimal("0.500"));
        spec.setPhysicalStockQty(physicalStockQty);
        spec.setReservedQty(BigDecimal.ZERO);
        spec.setWarningQty(BigDecimal.ONE);
        spec.setStatus("ACTIVE");
        return spec;
    }

    private AdminPrincipal principal() {
        return new AdminPrincipal(1L, "admin", 0L);
    }
}
