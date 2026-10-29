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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import com.fishmall.order.application.service.UserOrderService;
import com.fishmall.order.api.param.ConfirmQuoteRequest;
import com.fishmall.order.api.param.CancelOrderRequest;
import com.fishmall.order.api.vo.UserOrderResponse;
import com.fishmall.catalog.api.vo.PageResponse;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderCreateService orderCreateService;
    private final UserOrderService userOrders;
    private final com.fishmall.payment.PaymentService payments;

    public OrderController(OrderCreateService orderCreateService, UserOrderService userOrders, com.fishmall.payment.PaymentService payments) {
        this.orderCreateService = orderCreateService;
        this.userOrders = userOrders;
        this.payments = payments;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserOrderResponse.ListItem>> list(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        return ApiResponse.success(userOrders.list(principal, status, page, pageSize), traceId(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserOrderResponse.Detail> detail(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(userOrders.detail(principal, id), traceId(request));
    }

    @PostMapping("/{id}/confirm-quote")
    public ApiResponse<UserOrderResponse.Detail> confirm(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody ConfirmQuoteRequest body, HttpServletRequest request) {
        return ApiResponse.success(userOrders.confirm(principal, id, body), traceId(request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<UserOrderResponse.Detail> cancel(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody CancelOrderRequest body, HttpServletRequest request) {
        payments.prepareCancellation(id, principal.wxUserId());
        return ApiResponse.success(userOrders.cancel(principal, id, body), traceId(request));
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
