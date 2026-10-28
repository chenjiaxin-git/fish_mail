package com.fishmall.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.admin.config.AdminProperties;
import com.fishmall.admin.security.AdminJwtTokenService;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.config.UserSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserJwtTokenServiceTest {
    private final UserSecurityProperties properties = new UserSecurityProperties(
            "01234567890123456789012345678901", 3600);
    private final UserJwtTokenService service = new UserJwtTokenService(properties, new ObjectMapper());

    @Test
    void tokenRoundTripCarriesUserIdentity() {
        UserJwtTokenService.TokenIssueResult issued = service.issue(new UserPrincipal(7L));
        UserPrincipal parsed = service.parse(issued.token());
        assertThat(parsed.wxUserId()).isEqualTo(7L);
        assertThat(issued.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void tamperedTokenIsRejected() {
        UserJwtTokenService.TokenIssueResult issued = service.issue(new UserPrincipal(7L));
        String tampered = issued.token().substring(0, issued.token().length() - 1) + "x";
        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED);
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        UserJwtTokenService shortLived = new UserJwtTokenService(
                new UserSecurityProperties("01234567890123456789012345678901", 1), new ObjectMapper());
        String token = shortLived.issue(new UserPrincipal(7L)).token();
        Thread.sleep(1100);
        assertThatThrownBy(() -> shortLived.parse(token))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED);
    }

    @Test
    void adminTokenIsRejectedByUserService() {
        AdminJwtTokenService adminService = new AdminJwtTokenService(new AdminProperties(
                "admin", "initial", "01234567890123456789012345678901", 3600), new ObjectMapper());
        String adminToken = adminService.issue(new AdminPrincipal(1L, "admin", 0L)).token();
        assertThatThrownBy(() -> service.parse(adminToken))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED);
    }

    @Test
    void userTokenIsRejectedByAdminService() {
        AdminJwtTokenService adminService = new AdminJwtTokenService(new AdminProperties(
                "admin", "initial", "01234567890123456789012345678901", 3600), new ObjectMapper());
        String userToken = service.issue(new UserPrincipal(7L)).token();
        assertThatThrownBy(() -> adminService.parse(userToken))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED);
    }
}
