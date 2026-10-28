package com.fishmall.user.api.controller;

import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import com.fishmall.user.api.vo.UserInfoResponse;
import com.fishmall.user.application.WxUserAuthService;
import com.fishmall.user.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final WxUserAuthService authService;

    public UserController(WxUserAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> current(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(authService.current(principal), traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
