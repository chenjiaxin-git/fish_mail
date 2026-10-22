package com.fishmall.catalog.application.service;

import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.param.CategoryRequest;
import com.fishmall.catalog.api.vo.CategoryResponse;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.domain.ProductCategory;
import com.fishmall.catalog.repository.ProductCategoryMapper;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminCategoryService {
    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final AdminOperationLogMapper operationLogMapper;

    public AdminCategoryService(
            ProductCategoryMapper categoryMapper,
            ProductMapper productMapper,
            AdminOperationLogMapper operationLogMapper
    ) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.operationLogMapper = operationLogMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(String status, int page, int pageSize) {
        validatePage(page, pageSize);
        String normalizedStatus = normalizeStatus(status);
        List<CategoryResponse> items = categoryMapper.selectPage(normalizedStatus, (page - 1) * pageSize, pageSize)
                .stream().map(this::toResponse).toList();
        return new PageResponse<>(categoryMapper.count(normalizedStatus), page, pageSize, items);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request, AdminPrincipal principal, String traceId) {
        String name = request.name().trim();
        if (categoryMapper.findActiveByName(name).isPresent()) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATE);
        }
        ProductCategory category = new ProductCategory();
        category.setName(name);
        category.setSort(request.sort());
        category.setStatus(request.status());
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATE);
        }
        operationLogMapper.insert(principal.adminId(), principal.username(), "CATEGORY", "CREATE",
                String.valueOf(category.getId()), "SUCCESS", null, traceId);
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request, AdminPrincipal principal, String traceId) {
        ProductCategory category = categoryMapper.findActiveById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        String name = request.name().trim();
        if (categoryMapper.findActiveByNameExcludingId(name, id).isPresent()) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATE);
        }
        category.setName(name);
        category.setSort(request.sort());
        category.setStatus(request.status());
        try {
            if (categoryMapper.update(category) != 1) {
                throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATE);
        }
        operationLogMapper.insert(principal.adminId(), principal.username(), "CATEGORY", "UPDATE",
                String.valueOf(id), "SUCCESS", null, traceId);
        return toResponse(category);
    }

    @Transactional
    public void delete(Long id, AdminPrincipal principal, String traceId) {
        categoryMapper.findActiveById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (productMapper.countActiveByCategoryId(id) > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_PRODUCTS);
        }
        if (categoryMapper.softDelete(id) != 1) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        operationLogMapper.insert(principal.adminId(), principal.username(), "CATEGORY", "DELETE",
                String.valueOf(id), "SUCCESS", null, traceId);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分类状态不合法");
        }
        return status;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
    }

    private CategoryResponse toResponse(ProductCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSort(), category.getStatus(),
                category.getCreatedAt(), category.getUpdatedAt());
    }
}
