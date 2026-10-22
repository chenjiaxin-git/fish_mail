package com.fishmall.catalog.application;

import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.param.CategoryRequest;
import com.fishmall.catalog.application.service.AdminCategoryService;
import com.fishmall.catalog.domain.ProductCategory;
import com.fishmall.catalog.repository.ProductCategoryMapper;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {
    @Mock
    private ProductCategoryMapper categoryMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private AdminOperationLogMapper operationLogMapper;

    private AdminCategoryService service;

    @BeforeEach
    void setUp() {
        service = new AdminCategoryService(categoryMapper, productMapper, operationLogMapper);
    }

    @Test
    void createRejectsDuplicateCategoryName() {
        when(categoryMapper.findActiveByName("淡水鱼")).thenReturn(Optional.of(new ProductCategory()));

        assertThatThrownBy(() -> service.create(new CategoryRequest("淡水鱼", 0, "ACTIVE"), principal(), "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATE);
    }

    @Test
    void deleteRejectsCategoryWithProducts() {
        ProductCategory category = new ProductCategory();
        category.setId(1L);
        when(categoryMapper.findActiveById(1L)).thenReturn(Optional.of(category));
        when(productMapper.countActiveByCategoryId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L, principal(), "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.CATEGORY_HAS_PRODUCTS);
    }

    private AdminPrincipal principal() {
        return new AdminPrincipal(1L, "admin", 0L);
    }
}
