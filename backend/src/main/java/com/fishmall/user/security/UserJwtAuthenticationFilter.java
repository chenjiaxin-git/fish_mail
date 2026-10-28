package com.fishmall.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.common.ApiResponse;
import com.fishmall.common.BusinessException;
import com.fishmall.common.TraceIdFilter;
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
public class UserJwtAuthenticationFilter extends OncePerRequestFilter {
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/v1/addresses", "/api/v1/cart", "/api/v1/orders", "/api/v1/users");

    private final UserJwtTokenService tokenService;
    private final ObjectMapper objectMapper;

    public UserJwtAuthenticationFilter(UserJwtTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isProtectedPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                UserPrincipal principal = tokenService.parse(authorization.substring(7));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(() -> "ROLE_USER"));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException exception) {
                writeUnauthorized(request, response, exception);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProtectedPath(String uri) {
        for (String prefix : PROTECTED_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
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
