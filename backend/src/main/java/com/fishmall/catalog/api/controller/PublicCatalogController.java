package com.fishmall.catalog.api.controller;

import com.fishmall.catalog.api.vo.BannerResponse;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.api.vo.PublicCategoryResponse;
import com.fishmall.catalog.api.vo.PublicProductListResponse;
import com.fishmall.catalog.application.service.PublicCatalogService;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PublicCatalogController {
    private final PublicCatalogService catalogService;

    public PublicCatalogController(PublicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/banners")
    public ApiResponse<List<BannerResponse>> banners(HttpServletRequest request) {
        return ApiResponse.success(catalogService.banners(), traceId(request));
    }

    @GetMapping("/categories")
    public ApiResponse<List<PublicCategoryResponse>> categories(HttpServletRequest request) {
        return ApiResponse.success(catalogService.categories(), traceId(request));
    }

    @GetMapping("/products")
    public ApiResponse<PageResponse<PublicProductListResponse>> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request
    ) {
        return ApiResponse.success(catalogService.products(keyword, categoryId, page, pageSize), traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
