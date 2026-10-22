package com.fishmall.catalog.application.service;

import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.param.ProductRequest;
import com.fishmall.catalog.api.param.ProductSpecRequest;
import com.fishmall.catalog.api.param.StockAdjustRequest;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.api.vo.ProductDetailResponse;
import com.fishmall.catalog.api.vo.ProductListResponse;
import com.fishmall.catalog.api.vo.ProductSpecResponse;
import com.fishmall.catalog.api.vo.StockAdjustResponse;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
public class AdminProductService {
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductSpecMapper specMapper;
    private final ProductStockLedgerMapper stockLedgerMapper;
    private final AdminOperationLogMapper operationLogMapper;

    public AdminProductService(
            ProductMapper productMapper,
            ProductCategoryMapper categoryMapper,
            ProductSpecMapper specMapper,
            ProductStockLedgerMapper stockLedgerMapper,
            AdminOperationLogMapper operationLogMapper
    ) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.specMapper = specMapper;
        this.stockLedgerMapper = stockLedgerMapper;
        this.operationLogMapper = operationLogMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductListResponse> list(String keyword, Long categoryId, String status, int page, int pageSize) {
        validatePage(page, pageSize);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品名称搜索词不能超过100个字符");
        }
        String normalizedStatus = normalizeProductStatus(status);
        List<ProductListResponse> items = new ArrayList<>();
        for (Product product : productMapper.selectPage(normalizedKeyword, categoryId, normalizedStatus,
                (page - 1) * pageSize, pageSize)) {
            items.add(toListResponse(product));
        }
        return new PageResponse<>(productMapper.count(normalizedKeyword, categoryId, normalizedStatus), page, pageSize, items);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse detail(Long id) {
        Product product = requireProduct(id);
        return toDetailResponse(product);
    }

    @Transactional
    public ProductDetailResponse create(ProductRequest request, AdminPrincipal principal, String traceId) {
        validateCategory(request.categoryId());
        Product product = toProduct(null, request);
        productMapper.insert(product);
        saveSpecs(product, request.specs(), principal, true);
        operationLogMapper.insert(principal.adminId(), principal.username(), "PRODUCT", "CREATE",
                String.valueOf(product.getId()), "SUCCESS", null, traceId);
        return toDetailResponse(product);
    }

    @Transactional
    public ProductDetailResponse update(Long id, ProductRequest request, AdminPrincipal principal, String traceId) {
        Product product = requireProduct(id);
        validateCategory(request.categoryId());
        Product updated = toProduct(id, request);
        productMapper.update(updated);
        saveSpecs(updated, request.specs(), principal, false);
        operationLogMapper.insert(principal.adminId(), principal.username(), "PRODUCT", "UPDATE",
                String.valueOf(id), "SUCCESS", null, traceId);
        return toDetailResponse(updated);
    }

    @Transactional
    public void delete(Long id, AdminPrincipal principal, String traceId) {
        requireProduct(id);
        if (specMapper.sumReservedByProductId(id).compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCode.PRODUCT_HAS_RESERVATION);
        }
        if (productMapper.softDelete(id) != 1) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        for (ProductSpec spec : specMapper.findActiveByProductId(id)) {
            specMapper.softDelete(spec.getId(), id);
        }
        operationLogMapper.insert(principal.adminId(), principal.username(), "PRODUCT", "DELETE",
                String.valueOf(id), "SUCCESS", null, traceId);
    }

    @Transactional
    public StockAdjustResponse adjustStock(Long productId, Long specId, StockAdjustRequest request,
                                           AdminPrincipal principal, String traceId) {
        ProductSpec spec = specMapper.findActiveByIdAndProductId(specId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND));
        validateTargetQuantity(spec, request.targetQuantity());
        if (request.targetQuantity().compareTo(spec.getReservedQty()) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "调整后的库存不能小于已预占数量");
        }
        BigDecimal before = spec.getPhysicalStockQty();
        int updated = specMapper.updatePhysicalStock(specId, before, request.targetQuantity());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.STOCK_VERSION_CONFLICT);
        }
        ProductStockLedger ledger = new ProductStockLedger();
        ledger.setProductSpecId(specId);
        ledger.setChangeType("ADMIN_ADJUST");
        ledger.setQuantity(request.targetQuantity().subtract(before));
        ledger.setPhysicalBefore(before);
        ledger.setPhysicalAfter(request.targetQuantity());
        ledger.setReservedBefore(spec.getReservedQty());
        ledger.setReservedAfter(spec.getReservedQty());
        ledger.setReason(request.reason().trim());
        ledger.setOperatorType("ADMIN");
        ledger.setOperatorId(principal.adminId());
        ledger.setBizKey("ADMIN_ADJUST:" + specId + ":" + UUID.randomUUID());
        ledger.setCreatedAt(LocalDateTime.now());
        stockLedgerMapper.insert(ledger);
        operationLogMapper.insert(principal.adminId(), principal.username(), "PRODUCT", "STOCK_ADJUST",
                String.valueOf(specId), "SUCCESS", request.reason().trim(), traceId);
        BigDecimal available = request.targetQuantity().subtract(spec.getReservedQty());
        return new StockAdjustResponse(specId, before, request.targetQuantity(), spec.getReservedQty(), available,
                toLedgerResponse(ledger));
    }

    @Transactional(readOnly = true)
    public List<StockAdjustResponse.StockLedgerResponse> listStockLedger(Long productId, Long specId) {
        specMapper.findActiveByIdAndProductId(specId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND));
        return stockLedgerMapper.selectBySpecId(specId, 50).stream().map(this::toLedgerResponse).toList();
    }

    private void saveSpecs(Product product, List<ProductSpecRequest> requests, AdminPrincipal principal, boolean creating) {
        Map<Long, ProductSpec> existingById = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        if (!creating) {
            for (ProductSpec existing : specMapper.findActiveByProductId(product.getId())) {
                existingById.put(existing.getId(), existing);
            }
        }
        Set<Long> retainedIds = new HashSet<>();
        for (ProductSpecRequest request : requests) {
            validateSpecRequest(request);
            String normalizedName = request.name().trim();
            if (!names.add(normalizedName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "规格名称不能重复");
            }
            if (request.id() == null) {
                ProductSpec spec = toSpec(product.getId(), request);
                insertSpecAndInitialLedger(spec, principal);
                continue;
            }
            ProductSpec existing = existingById.get(request.id());
            if (existing == null) {
                throw new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND);
            }
            retainedIds.add(existing.getId());
            boolean hasReservation = existing.getReservedQty().compareTo(BigDecimal.ZERO) > 0;
            if (request.physicalStockQty().compareTo(existing.getPhysicalStockQty()) != 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "已有库存请通过库存调整接口修改");
            }
            if (hasReservation && (!request.quantityKind().equals(existing.getQuantityKind())
                    || !request.unit().equals(existing.getUnit())
                    || request.minQuantity().compareTo(existing.getMinQuantity()) != 0
                    || request.stepQuantity().compareTo(existing.getStepQuantity()) != 0)) {
                throw new BusinessException(ErrorCode.PRODUCT_SPEC_IN_USE);
            }
            ProductSpec spec = toSpec(product.getId(), request);
            spec.setId(existing.getId());
            spec.setPhysicalStockQty(existing.getPhysicalStockQty());
            spec.setReservedQty(existing.getReservedQty());
            try {
                if (specMapper.update(spec) != 1) {
                    throw new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND);
                }
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "规格名称已存在");
            }
        }
        for (ProductSpec existing : existingById.values()) {
            if (retainedIds.contains(existing.getId())) continue;
            if (existing.getReservedQty().compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(ErrorCode.PRODUCT_SPEC_IN_USE);
            }
            specMapper.softDelete(existing.getId(), product.getId());
        }
    }

    private void insertSpecAndInitialLedger(ProductSpec spec, AdminPrincipal principal) {
        try {
            specMapper.insert(spec);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "规格名称已存在");
        }
        ProductStockLedger ledger = new ProductStockLedger();
        ledger.setProductSpecId(spec.getId());
        ledger.setChangeType("INITIAL");
        ledger.setQuantity(spec.getPhysicalStockQty());
        ledger.setPhysicalBefore(BigDecimal.ZERO);
        ledger.setPhysicalAfter(spec.getPhysicalStockQty());
        ledger.setReservedBefore(BigDecimal.ZERO);
        ledger.setReservedAfter(BigDecimal.ZERO);
        ledger.setReason("商品规格初始库存");
        ledger.setOperatorType("ADMIN");
        ledger.setOperatorId(principal.adminId());
        ledger.setBizKey("INITIAL:" + spec.getId() + ":" + UUID.randomUUID());
        stockLedgerMapper.insert(ledger);
    }

    private Product requireProduct(Long id) {
        return productMapper.findActiveById(id).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private void validateCategory(Long categoryId) {
        ProductCategory category = categoryMapper.findActiveById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!"ACTIVE".equals(category.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分类已停用");
        }
    }

    private void validateSpecRequest(ProductSpecRequest request) {
        if (request.priceCent() == null || request.priceCent() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "单价必须大于0");
        }
        if (request.minQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "最小购买量必须大于0");
        }
        if (request.stepQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "步进值必须大于0");
        }
        validateQuantity(request.quantityKind(), request.minQuantity(), "最小购买量");
        validateQuantity(request.quantityKind(), request.stepQuantity(), "步进值");
        validateQuantity(request.quantityKind(), request.physicalStockQty(), "库存");
        validateQuantity(request.quantityKind(), request.warningQty(), "预警量");
    }

    private void validateTargetQuantity(ProductSpec spec, BigDecimal quantity) {
        validateQuantity(spec.getQuantityKind(), quantity, "库存");
    }

    private void validateQuantity(String quantityKind, BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "不能小于0");
        }
        if (value.stripTrailingZeros().scale() > 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "最多三位小数");
        }
        if ("PIECE".equals(quantityKind) && value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "必须为整数");
        }
    }

    private String normalizeProductStatus(String status) {
        if (status == null || status.isBlank()) return null;
        if (!"ACTIVE".equals(status) && !"OFF_SHELF".equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品状态不合法");
        }
        return status;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
    }

    private Product toProduct(Long id, ProductRequest request) {
        Product product = new Product();
        product.setId(id);
        product.setCategoryId(request.categoryId());
        product.setName(request.name().trim());
        product.setSubtitle(request.subtitle());
        product.setDescription(request.description());
        product.setMainImageUrl(request.mainImageUrl());
        product.setStatus(request.status());
        return product;
    }

    private ProductSpec toSpec(Long productId, ProductSpecRequest request) {
        ProductSpec spec = new ProductSpec();
        spec.setProductId(productId);
        spec.setName(request.name().trim());
        spec.setQuantityKind(request.quantityKind());
        spec.setUnit(request.unit().trim());
        spec.setPriceCent(request.priceCent());
        spec.setMinQuantity(request.minQuantity());
        spec.setStepQuantity(request.stepQuantity());
        spec.setPhysicalStockQty(request.physicalStockQty());
        spec.setWarningQty(request.warningQty());
        spec.setStatus(request.status());
        return spec;
    }

    private ProductListResponse toListResponse(Product product) {
        List<ProductSpec> specs = specMapper.findActiveByProductId(product.getId());
        Long minPrice = null;
        Long maxPrice = null;
        BigDecimal physical = BigDecimal.ZERO;
        BigDecimal reserved = BigDecimal.ZERO;
        for (ProductSpec spec : specs) {
            minPrice = minPrice == null ? spec.getPriceCent() : Math.min(minPrice, spec.getPriceCent());
            maxPrice = maxPrice == null ? spec.getPriceCent() : Math.max(maxPrice, spec.getPriceCent());
            physical = physical.add(spec.getPhysicalStockQty());
            reserved = reserved.add(spec.getReservedQty());
        }
        return new ProductListResponse(product.getId(), product.getCategoryId(), categoryName(product.getCategoryId()),
                product.getName(), product.getSubtitle(), product.getMainImageUrl(), product.getStatus(), specs.size(),
                minPrice, maxPrice, physical, reserved, physical.subtract(reserved), product.getCreatedAt(), product.getUpdatedAt());
    }

    private ProductDetailResponse toDetailResponse(Product product) {
        List<ProductSpecResponse> specs = specMapper.findActiveByProductId(product.getId()).stream()
                .map(this::toSpecResponse).toList();
        return new ProductDetailResponse(product.getId(), product.getCategoryId(), categoryName(product.getCategoryId()),
                product.getName(), product.getSubtitle(), product.getDescription(), product.getMainImageUrl(), product.getStatus(),
                specs, product.getCreatedAt(), product.getUpdatedAt());
    }

    private String categoryName(Long categoryId) {
        return categoryMapper.findActiveById(categoryId).map(ProductCategory::getName).orElse("已删除分类");
    }

    private ProductSpecResponse toSpecResponse(ProductSpec spec) {
        return new ProductSpecResponse(spec.getId(), spec.getProductId(), spec.getName(), spec.getQuantityKind(), spec.getUnit(),
                spec.getPriceCent(), spec.getMinQuantity(), spec.getStepQuantity(), spec.getPhysicalStockQty(), spec.getReservedQty(),
                spec.getPhysicalStockQty().subtract(spec.getReservedQty()), spec.getWarningQty(), spec.getStatus(),
                spec.getCreatedAt(), spec.getUpdatedAt());
    }

    private StockAdjustResponse.StockLedgerResponse toLedgerResponse(ProductStockLedger ledger) {
        return new StockAdjustResponse.StockLedgerResponse(ledger.getId(), ledger.getChangeType(), ledger.getQuantity(),
                ledger.getPhysicalBefore(), ledger.getPhysicalAfter(), ledger.getReservedBefore(), ledger.getReservedAfter(),
                ledger.getReason(), ledger.getBizKey(), ledger.getCreatedAt());
    }
}
