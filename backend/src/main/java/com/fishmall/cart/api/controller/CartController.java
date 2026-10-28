package com.fishmall.cart.api.controller;

import com.fishmall.cart.api.param.CartAddRequest;
import com.fishmall.cart.api.param.CartUpdateRequest;
import com.fishmall.cart.api.vo.CartItemResponse;
import com.fishmall.cart.application.CartService;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import com.fishmall.user.security.UserPrincipal;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<List<CartItemResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(cartService.list(principal), traceId(request));
    }

    @PostMapping
    public ApiResponse<CartItemResponse> addItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CartAddRequest addRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(cartService.addItem(principal, addRequest), traceId(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CartItemResponse> updateItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CartUpdateRequest updateRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(cartService.updateItem(principal, id, updateRequest), traceId(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        cartService.deleteItem(principal, id);
        return ApiResponse.success(null, traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
