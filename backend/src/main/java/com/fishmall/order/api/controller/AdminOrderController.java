package com.fishmall.order.api.controller;

import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import com.fishmall.order.api.param.CancelOrderRequest;
import com.fishmall.order.api.param.QuoteRequest;
import com.fishmall.order.api.param.RefundConfirmRequest;
import com.fishmall.order.api.vo.OrderDetailResponse;
import com.fishmall.order.api.vo.OrderListResponse;
import com.fishmall.order.application.service.AdminOrderExportService;
import com.fishmall.order.application.service.AdminOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final AdminOrderService orderService;
    private final AdminOrderExportService exportService;

    public AdminOrderController(AdminOrderService orderService, AdminOrderExportService exportService) {
        this.orderService = orderService;
        this.exportService = exportService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderListResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(orderService.list(status, orderNo, phone, startDate, endDate, page, pageSize,
                principal, traceId(request)), traceId(request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String phone,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        byte[] content = exportService.export(status, orderNo, phone, startDate, endDate, principal, traceId(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orders.xlsx")
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> detail(@PathVariable Long id,
                                                   @AuthenticationPrincipal AdminPrincipal principal,
                                                   HttpServletRequest request) {
        return ApiResponse.success(orderService.detail(id, principal, traceId(request)), traceId(request));
    }

    @PostMapping("/{id}/quote")
    public ApiResponse<OrderDetailResponse> quote(@PathVariable Long id,
                                                  @Valid @RequestBody QuoteRequest body,
                                                  @AuthenticationPrincipal AdminPrincipal principal,
                                                  HttpServletRequest request) {
        return ApiResponse.success(orderService.quote(id, body, principal, traceId(request)), traceId(request));
    }

    @PostMapping("/{id}/deliver")
    public ApiResponse<OrderDetailResponse> deliver(@PathVariable Long id,
                                                    @AuthenticationPrincipal AdminPrincipal principal,
                                                    HttpServletRequest request) {
        return ApiResponse.success(orderService.deliver(id, principal, traceId(request)), traceId(request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<OrderDetailResponse> complete(@PathVariable Long id,
                                                     @AuthenticationPrincipal AdminPrincipal principal,
                                                     HttpServletRequest request) {
        return ApiResponse.success(orderService.complete(id, principal, traceId(request)), traceId(request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderDetailResponse> cancel(@PathVariable Long id,
                                                   @Valid @RequestBody CancelOrderRequest body,
                                                   @AuthenticationPrincipal AdminPrincipal principal,
                                                   HttpServletRequest request) {
        return ApiResponse.success(orderService.cancel(id, body, principal, traceId(request)), traceId(request));
    }

    @PostMapping("/{id}/refund-confirmed")
    public ApiResponse<OrderDetailResponse> refundConfirmed(@PathVariable Long id,
                                                            @Valid @RequestBody RefundConfirmRequest body,
                                                            @AuthenticationPrincipal AdminPrincipal principal,
                                                            HttpServletRequest request) {
        return ApiResponse.success(orderService.refundConfirmed(id, body, principal, traceId(request)), traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
