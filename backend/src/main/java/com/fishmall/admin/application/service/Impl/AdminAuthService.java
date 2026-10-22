package com.fishmall.admin.application;

import com.fishmall.admin.api.AdminAuthRequest;
import com.fishmall.admin.api.AdminInfoResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
    private final AdminUserMapper adminUserMapper;
    private final AdminAuthLogMapper authLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtTokenService tokenService;
    private final AdminProperties properties;

    public AdminAuthService(
            AdminUserMapper adminUserMapper,
            AdminAuthLogMapper authLogMapper,
            PasswordEncoder passwordEncoder,
            AdminJwtTokenService tokenService,
            AdminProperties properties
    ) {
        this.adminUserMapper = adminUserMapper;
        this.authLogMapper = authLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Transactional
    public TokenResponse login(AdminAuthRequest request, String ip, String userAgent, String traceId) {
        AdminUser admin = properties.username().equals(request.username())
                ? adminUserMapper.findByUsername(request.username()).orElse(null)
                : null;
        boolean valid = admin != null
                && "ACTIVE".equals(admin.getStatus())
                && passwordEncoder.matches(request.password(), admin.getPasswordHash());
        if (!valid) {
            authLogMapper.insert(request.username(), "LOGIN_FAILED", "FAILED",
                    ErrorCode.AUTH_INVALID_CREDENTIALS.code(), ip, userAgent, traceId);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        adminUserMapper.updateLastLogin(admin.getId());
        authLogMapper.insert(admin.getUsername(), "LOGIN", "SUCCESS", null, ip, userAgent, traceId);
        return issueToken(admin);
    }

    @Transactional
    public TokenResponse changePassword(
            AdminPrincipal principal,
            ChangePasswordRequest request,
            String ip,
            String userAgent,
            String traceId
    ) {
        AdminUser admin = adminUserMapper.findById(principal.adminId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED));
        if (!"ACTIVE".equals(admin.getStatus())
                || !admin.getTokenVersion().equals(principal.tokenVersion())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            authLogMapper.insert(admin.getUsername(), "PASSWORD_CHANGE_FAILED", "FAILED",
                    ErrorCode.PASSWORD_CURRENT_MISMATCH.code(), ip, userAgent, traceId);
            throw new BusinessException(ErrorCode.PASSWORD_CURRENT_MISMATCH);
        }
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_INVALID, "新密码不能与当前密码相同");
        }
        int updated = adminUserMapper.updatePassword(
                admin.getId(), passwordEncoder.encode(request.newPassword()), admin.getTokenVersion());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
        admin.setTokenVersion(admin.getTokenVersion() + 1);
        authLogMapper.insert(admin.getUsername(), "PASSWORD_CHANGED", "SUCCESS", null, ip, userAgent, traceId);
        return issueToken(admin);
    }

    @Transactional(readOnly = true)
    public AdminInfoResponse current(AdminPrincipal principal) {
        AdminUser admin = adminUserMapper.findById(principal.adminId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED));
        if (!"ACTIVE".equals(admin.getStatus()) || !admin.getTokenVersion().equals(principal.tokenVersion())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
        return new AdminInfoResponse(admin.getId(), admin.getUsername(), admin.getStatus());
    }

    private TokenResponse issueToken(AdminUser admin) {
        AdminPrincipal principal = new AdminPrincipal(admin.getId(), admin.getUsername(), admin.getTokenVersion());
        AdminJwtTokenService.TokenIssueResult token = tokenService.issue(principal);
        return new TokenResponse(
                token.token(),
                "Bearer",
                token.expiresIn(),
                new AdminInfoResponse(admin.getId(), admin.getUsername(), admin.getStatus())
        );
    }
}
