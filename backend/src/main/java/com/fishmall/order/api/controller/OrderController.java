package com.fishmall.order.api.controller;

import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import com.fishmall.order.api.param.CreateOrderRequest;
import com.fishmall.order.api.vo.OrderCreateResponse;
import com.fishmall.order.application.service.OrderCreateService;
import com.fishmall.user.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderCreateService orderCreateService;

    public OrderController(OrderCreateService orderCreateService) {
        this.orderCreateService = orderCreateService;
    }

    @PostMapping
    public ApiResponse<OrderCreateResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(orderCreateService.create(principal, request), traceId(httpRequest));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
