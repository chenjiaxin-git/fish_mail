package com.fishmall.catalog.api.controller;

import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.param.ProductRequest;
import com.fishmall.catalog.api.param.StockAdjustRequest;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.api.vo.ProductDetailResponse;
import com.fishmall.catalog.api.vo.ProductListResponse;
import com.fishmall.catalog.api.vo.StockAdjustResponse;
import com.fishmall.catalog.application.service.AdminProductService;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {
    private final AdminProductService productService;

    public AdminProductController(AdminProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductListResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request
    ) {
        return ApiResponse.success(productService.list(keyword, categoryId, status, page, pageSize), traceId(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> detail(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(productService.detail(id), traceId(request));
    }

    @PostMapping
    public ApiResponse<ProductDetailResponse> create(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody ProductRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(productService.create(body, principal, traceId(request)), traceId(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductDetailResponse> update(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(productService.update(id, body, principal, traceId(request)), traceId(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        productService.delete(id, principal, traceId(request));
        return ApiResponse.success(null, traceId(request));
    }

    @PostMapping("/{id}/specs/{specId}/stock")
    public ApiResponse<StockAdjustResponse> adjustStock(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long specId,
            @Valid @RequestBody StockAdjustRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(productService.adjustStock(id, specId, body, principal, traceId(request)), traceId(request));
    }

    @GetMapping("/{id}/specs/{specId}/stock-ledger")
    public ApiResponse<List<StockAdjustResponse.StockLedgerResponse>> stockLedger(
            @PathVariable Long id,
            @PathVariable Long specId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(productService.listStockLedger(id, specId), traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
