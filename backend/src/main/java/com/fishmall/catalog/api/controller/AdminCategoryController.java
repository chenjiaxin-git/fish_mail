package com.fishmall.catalog.api.controller;

import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.param.CategoryRequest;
import com.fishmall.catalog.api.vo.CategoryResponse;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.application.service.AdminCategoryService;
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

@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {
    private final AdminCategoryService categoryService;

    public AdminCategoryController(AdminCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CategoryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize,
            HttpServletRequest request
    ) {
        return ApiResponse.success(categoryService.list(status, page, pageSize), traceId(request));
    }

    @PostMapping
    public ApiResponse<CategoryResponse> create(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody CategoryRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(categoryService.create(body, principal, traceId(request)), traceId(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(categoryService.update(id, body, principal, traceId(request)), traceId(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        categoryService.delete(id, principal, traceId(request));
        return ApiResponse.success(null, traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
