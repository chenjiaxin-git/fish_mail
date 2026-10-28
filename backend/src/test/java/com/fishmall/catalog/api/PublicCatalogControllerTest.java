package com.fishmall.catalog.api;

import com.fishmall.catalog.api.controller.PublicCatalogController;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.api.vo.PublicCategoryResponse;
import com.fishmall.catalog.api.vo.PublicProductDetailResponse;
import com.fishmall.catalog.api.vo.PublicProductSpecResponse;
import com.fishmall.catalog.application.service.PublicCatalogService;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicCatalogControllerTest {
    @Mock
    private PublicCatalogService catalogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicCatalogController(catalogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void bannersEndpointReturnsUnifiedResponse() throws Exception {
        when(catalogService.banners()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void categoriesEndpointReturnsUnifiedResponse() throws Exception {
        when(catalogService.categories()).thenReturn(List.of(new PublicCategoryResponse(1L, "淡水鱼", 1)));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].name").value("淡水鱼"));
    }

    @Test
    void productsEndpointPassesCatalogFilters() throws Exception {
        when(catalogService.products(eq("鱼"), eq(2L), eq(1), eq(20)))
                .thenReturn(new PageResponse<>(0, 1, 20, Collections.emptyList()));

        mockMvc.perform(get("/api/v1/products")
                        .param("keyword", "鱼")
                        .param("categoryId", "2")
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void productDetailEndpointReturnsUnifiedResponse() throws Exception {
        PublicProductSpecResponse spec = new PublicProductSpecResponse(20L, 10L, "按斤", "WEIGHT", "kg", 3000L,
                BigDecimal.ONE, new BigDecimal("0.500"), new BigDecimal("2.000"));
        PublicProductDetailResponse detail = new PublicProductDetailResponse(10L, 1L, "淡水鱼", "草鱼", "鲜活",
                "适合清蒸", "/uploads/products/10.jpg", 3000L, 4500L, new BigDecimal("4.000"), List.of(spec));
        when(catalogService.productDetail(10L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/products/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.name").value("草鱼"))
                .andExpect(jsonPath("$.data.description").value("适合清蒸"))
                .andExpect(jsonPath("$.data.minPriceCent").value(3000))
                .andExpect(jsonPath("$.data.specs[0].name").value("按斤"))
                .andExpect(jsonPath("$.data.specs[0].priceCent").value(3000));
    }

    @Test
    void productDetailEndpointRejectsInvalidId() throws Exception {
        mockMvc.perform(get("/api/v1/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.code()));
    }

    @Test
    void productDetailEndpointReturnsNotFoundCode() throws Exception {
        when(catalogService.productDetail(99L)).thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_NOT_FOUND.code()));
    }
}
