package com.fishmall.user.api.controller;

import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import com.fishmall.user.api.param.WechatLoginRequest;
import com.fishmall.user.api.vo.UserTokenResponse;
import com.fishmall.user.application.WxUserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WechatAuthController {
    private final WxUserAuthService authService;

    public WechatAuthController(WxUserAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/wechat")
    public ApiResponse<UserTokenResponse> login(
            @Valid @RequestBody WechatLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(authService.login(request), traceId(httpRequest));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
