package com.fishmall.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.api.param.WechatLoginRequest;
import com.fishmall.user.api.vo.UserTokenResponse;
import com.fishmall.user.config.UserSecurityProperties;
import com.fishmall.user.domain.WxUser;
import com.fishmall.user.infrastructure.WeChatAuthClient;
import com.fishmall.user.repository.WxUserMapper;
import com.fishmall.user.security.UserJwtTokenService;
import com.fishmall.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WxUserAuthServiceTest {
    @Mock private WeChatAuthClient weChatAuthClient;
    @Mock private WxUserMapper wxUserMapper;

    private WxUserAuthService service;

    @BeforeEach
    void setUp() {
        UserJwtTokenService tokenService = new UserJwtTokenService(
                new UserSecurityProperties("01234567890123456789012345678901", 3600), new ObjectMapper());
        service = new WxUserAuthService(weChatAuthClient, wxUserMapper, tokenService);
    }

    @Test
    void loginCreatesNewUserAndIssuesToken() {
        when(weChatAuthClient.code2Session("code-1")).thenReturn("openid-1");
        when(wxUserMapper.findByOpenid("openid-1")).thenReturn(Optional.empty());
        when(wxUserMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, WxUser.class).setId(7L);
            return 1;
        });
        when(wxUserMapper.touchLogin(7L)).thenReturn(1);

        UserTokenResponse response = service.login(new WechatLoginRequest("code-1"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(3600L);
        verify(wxUserMapper).insert(argThat(user -> "openid-1".equals(user.getOpenid())));
        verify(wxUserMapper).touchLogin(7L);
    }

    @Test
    void loginReusesExistingUser() {
        WxUser user = new WxUser();
        user.setId(7L);
        user.setOpenid("openid-1");
        user.setStatus("ACTIVE");
        when(weChatAuthClient.code2Session("code-1")).thenReturn("openid-1");
        when(wxUserMapper.findByOpenid("openid-1")).thenReturn(Optional.of(user));
        when(wxUserMapper.touchLogin(7L)).thenReturn(1);

        UserTokenResponse response = service.login(new WechatLoginRequest("code-1"));

        assertThat(response.accessToken()).isNotBlank();
        verify(wxUserMapper, never()).insert(any());
    }

    @Test
    void loginRejectsDisabledUser() {
        WxUser user = new WxUser();
        user.setId(7L);
        user.setOpenid("openid-1");
        user.setStatus("DISABLED");
        when(weChatAuthClient.code2Session("code-1")).thenReturn("openid-1");
        when(wxUserMapper.findByOpenid("openid-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new WechatLoginRequest("code-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    void loginRejectsWhenWechatFails() {
        when(weChatAuthClient.code2Session("code-1")).thenThrow(new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));

        assertThatThrownBy(() -> service.login(new WechatLoginRequest("code-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
        verify(wxUserMapper, never()).insert(any());
    }

    @Test
    void currentReturnsUserInfo() {
        WxUser user = new WxUser();
        user.setId(7L);
        user.setNickname("鱼粉");
        when(wxUserMapper.findById(7L)).thenReturn(Optional.of(user));

        assertThat(service.current(new UserPrincipal(7L)).nickname()).isEqualTo("鱼粉");
    }
}
