package com.fishmall.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.user.config.UserSecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserJwtAuthenticationFilterTest {
    private UserJwtAuthenticationFilter filter;
    private UserJwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new UserJwtTokenService(
                new UserSecurityProperties("01234567890123456789012345678901", 3600), new ObjectMapper());
        filter = new UserJwtAuthenticationFilter(tokenService, new ObjectMapper());
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenAuthenticatesProtectedRequest() throws Exception {
        String token = tokenService.issue(new UserPrincipal(7L)).token();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(UserPrincipal.class);
    }

    @Test
    void tamperedTokenReturns401OnProtectedRequest() throws Exception {
        String token = tokenService.issue(new UserPrincipal(7L)).token();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer " + token + "x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_REVOKED");
    }

    @Test
    void publicRequestIgnoresToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
