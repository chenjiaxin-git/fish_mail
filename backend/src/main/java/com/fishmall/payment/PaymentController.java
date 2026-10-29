package com.fishmall.payment;

import com.fishmall.common.ApiResponse;
import com.fishmall.common.BusinessException;
import com.fishmall.common.TraceIdFilter;
import com.fishmall.order.api.param.ConfirmQuoteRequest;
import com.fishmall.order.application.service.UserOrderService;
import com.fishmall.order.api.vo.UserOrderResponse;
import com.fishmall.user.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
public class PaymentController {
    private final PaymentService payments;
    private final UserOrderService orders;
    public PaymentController(PaymentService payments, UserOrderService orders) { this.payments = payments; this.orders = orders; }

    @PostMapping("/api/v1/orders/{id}/prepay")
    public ApiResponse<PaymentService.PrepayResponse> prepay(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody ConfirmQuoteRequest body, HttpServletRequest request) {
        return ApiResponse.success(payments.prepay(id, user.wxUserId(), body.quoteVersion()), trace(request));
    }
    @PostMapping("/api/v1/orders/{id}/sync-payment")
    public ApiResponse<UserOrderResponse.Detail> sync(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal user, HttpServletRequest request) {
        payments.sync(id, user.wxUserId());
        return ApiResponse.success(orders.detail(user, id), trace(request));
    }
    @PostMapping("/api/v1/payments/wechat/notify")
    public ResponseEntity<?> notify(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (String key : new String[]{"Wechatpay-Serial", "Wechatpay-Signature", "Wechatpay-Timestamp", "Wechatpay-Nonce", "Wechatpay-Signature-Type"}) {
            if (request.getHeader(key) != null) headers.put(key, request.getHeader(key));
        }
        try {
            byte[] bytes = request.getInputStream().readNBytes(65537);
            if (bytes.length > 65536) return ResponseEntity.badRequest().body(Map.of("code", "FAIL", "message", "通知过大"));
            String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            payments.notify(body, headers);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            int code = e instanceof BusinessException b && b.errorCode().httpStatus() < 500 ? 400 : 500;
            return ResponseEntity.status(code).body(Map.of("code", "FAIL", "message", "支付通知处理失败"));
        }
    }
    private String trace(HttpServletRequest r) { return String.valueOf(r.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)); }
}
