package com.fishmall.admin.security;

import com.fishmall.admin.application.AdminAuthService;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.BusinessException;
import com.fishmall.common.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {
    private final AdminJwtTokenService tokenService;
    private final AdminAuthService authService;
    private final ObjectMapper objectMapper;

    public AdminJwtAuthenticationFilter(
            AdminJwtTokenService tokenService,
            AdminAuthService authService,
            ObjectMapper objectMapper
    ) {
        this.tokenService = tokenService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                AdminPrincipal principal = tokenService.parse(authorization.substring(7));
                authService.current(principal);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(() -> "ROLE_ADMIN"));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException exception) {
                writeUnauthorized(request, response, exception);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, BusinessException exception)
            throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(
                exception.errorCode().code(),
                exception.getMessage(),
                String.valueOf(request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE))
        ));
    }
}
