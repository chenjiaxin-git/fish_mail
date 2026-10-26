package com.fishmall.catalog.api;

import com.fishmall.catalog.api.controller.PublicCatalogController;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.api.vo.PublicCategoryResponse;
import com.fishmall.catalog.application.service.PublicCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicCatalogController(catalogService)).build();
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
}
