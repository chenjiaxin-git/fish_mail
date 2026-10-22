package com.fishmall.admin.api;

import com.fishmall.admin.application.AdminAuthService;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuthController {
    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(
            @Valid @RequestBody AdminAuthRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(authService.login(
                request,
                ip(httpServletRequest),
                userAgent(httpServletRequest),
                traceId(httpServletRequest)
        ), traceId(httpServletRequest));
    }

    @GetMapping("/me")
    public ApiResponse<AdminInfoResponse> current(
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(authService.current(principal), traceId(request));
    }

    @PostMapping("/change-password")
    public ApiResponse<TokenResponse> changePassword(
            @AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(authService.changePassword(
                principal,
                request,
                ip(httpServletRequest),
                userAgent(httpServletRequest),
                traceId(httpServletRequest)
        ), traceId(httpServletRequest));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }

    private String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? "" : value.substring(0, Math.min(value.length(), 255));
    }
}
