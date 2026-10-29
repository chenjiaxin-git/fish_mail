package com.fishmall.admin.api.controller;

import com.fishmall.admin.api.vo.DashboardResponse;
import com.fishmall.admin.application.service.AdminDashboardService;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardService service;
    public AdminDashboardController(AdminDashboardService service) { this.service=service; }
    @GetMapping
    public ApiResponse<DashboardResponse> get(@AuthenticationPrincipal AdminPrincipal principal, HttpServletRequest request) {
        return ApiResponse.success(service.get(principal,String.valueOf(request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE))),
                String.valueOf(request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)));
    }
}
