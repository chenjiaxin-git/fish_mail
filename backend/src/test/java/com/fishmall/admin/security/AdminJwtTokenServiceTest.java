package com.fishmall.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.admin.config.AdminProperties;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminJwtTokenServiceTest {
    private final AdminProperties properties = new AdminProperties(
            "admin", "initial-password", "01234567890123456789012345678901", 3600);
    private final AdminJwtTokenService service = new AdminJwtTokenService(properties, new ObjectMapper());

    @Test
    void tokenRoundTripCarriesAdminIdentity() {
        AdminJwtTokenService.TokenIssueResult issued = service.issue(new AdminPrincipal(1L, "admin", 3L));
        AdminPrincipal parsed = service.parse(issued.token());
        assertThat(parsed.adminId()).isEqualTo(1L);
        assertThat(parsed.username()).isEqualTo("admin");
        assertThat(parsed.tokenVersion()).isEqualTo(3L);
    }

    @Test
    void tamperedTokenIsRejected() {
        AdminJwtTokenService.TokenIssueResult issued = service.issue(new AdminPrincipal(1L, "admin", 0L));
        String tampered = issued.token().substring(0, issued.token().length() - 1) + "x";
        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED);
    }
}
