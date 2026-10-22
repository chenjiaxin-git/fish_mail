package com.fishmall.admin.application;

import com.fishmall.admin.api.AdminAuthRequest;
import com.fishmall.admin.api.ChangePasswordRequest;
import com.fishmall.admin.api.TokenResponse;
import com.fishmall.admin.config.AdminProperties;
import com.fishmall.admin.domain.AdminUser;
import com.fishmall.admin.repository.AdminAuthLogMapper;
import com.fishmall.admin.repository.AdminUserMapper;
import com.fishmall.admin.security.AdminJwtTokenService;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {
    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private AdminAuthLogMapper authLogMapper;
    @Mock
    private AdminJwtTokenService tokenService;

    private AdminAuthService service;
    private AdminProperties properties;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        properties = new AdminProperties("admin", "initial-password", "01234567890123456789012345678901", 3600);
        service = new AdminAuthService(adminUserMapper, authLogMapper, passwordEncoder, tokenService, properties);
    }

    @Test
    void loginRejectsAccountOutsideSingleAdminBoundary() {
        assertThatThrownBy(() -> service.login(
                new AdminAuthRequest("someone-else", "password123"), "127.0.0.1", "test", "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        verify(authLogMapper).insert(
                "someone-else", "LOGIN_FAILED", "FAILED",
                ErrorCode.AUTH_INVALID_CREDENTIALS.code(), "127.0.0.1", "test", "trace");
    }

    @Test
    void loginRejectsWrongPasswordAndDisabledAccount() {
        AdminUser admin = admin("admin", "password123", "ACTIVE");
        when(adminUserMapper.findByUsername("admin")).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> service.login(
                new AdminAuthRequest("admin", "wrong-password"), "127.0.0.1", "test", "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);

        admin.setStatus("DISABLED");
        assertThatThrownBy(() -> service.login(
                new AdminAuthRequest("admin", "password123"), "127.0.0.1", "test", "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    void loginReturnsTokenForConfiguredAdmin() {
        AdminUser admin = admin("admin", "password123", "ACTIVE");
        when(adminUserMapper.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(tokenService.issue(any())).thenReturn(new AdminJwtTokenService.TokenIssueResult("token", 3600));

        TokenResponse response = service.login(
                new AdminAuthRequest("admin", "password123"), "127.0.0.1", "test", "trace");

        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(adminUserMapper).updateLastLogin(1L);
    }

    @Test
    void changePasswordRotatesTokenAndRejectsOldTokenVersion() {
        AdminUser admin = admin("admin", "password123", "ACTIVE");
        when(adminUserMapper.findById(1L)).thenReturn(Optional.of(admin));
        when(adminUserMapper.updatePassword(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(tokenService.issue(any())).thenReturn(new AdminJwtTokenService.TokenIssueResult("new-token", 3600));

        TokenResponse response = service.changePassword(
                new AdminPrincipal(1L, "admin", 0L),
                new ChangePasswordRequest("password123", "new-password", "new-password"),
                "127.0.0.1", "test", "trace");

        assertThat(response.accessToken()).isEqualTo("new-token");
        verify(adminUserMapper).updatePassword(anyLong(), anyString(), anyLong());
    }

    private AdminUser admin(String username, String password, String status) {
        AdminUser admin = new AdminUser();
        admin.setId(1L);
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setTokenVersion(0L);
        admin.setStatus(status);
        return admin;
    }
}
