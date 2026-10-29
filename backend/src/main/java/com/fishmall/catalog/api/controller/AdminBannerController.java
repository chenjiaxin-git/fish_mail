package com.fishmall.catalog.api.controller;

import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.param.BannerRequest;
import com.fishmall.catalog.api.vo.AdminBannerResponse;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.application.service.AdminBannerService;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/banners")
public class AdminBannerController {
    private final AdminBannerService service;

    public AdminBannerController(AdminBannerService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminBannerResponse>> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        return ApiResponse.success(service.list(status, page, pageSize, principal, trace(request)), trace(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminBannerResponse> detail(@PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        return ApiResponse.success(service.detail(id, principal, trace(request)), trace(request));
    }

    @PostMapping
    public ApiResponse<AdminBannerResponse> create(@Valid @RequestBody BannerRequest body,
            @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        return ApiResponse.success(service.create(body, principal, trace(request)), trace(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminBannerResponse> update(@PathVariable Long id, @Valid @RequestBody BannerRequest body,
            @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        return ApiResponse.success(service.update(id, body, principal, trace(request)), trace(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        service.delete(id, principal, trace(request));
        return ApiResponse.success(null, trace(request));
    }

    private String trace(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
