package com.fishmall.user.application;

import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.api.param.WechatLoginRequest;
import com.fishmall.user.api.vo.UserInfoResponse;
import com.fishmall.user.api.vo.UserTokenResponse;
import com.fishmall.user.domain.WxUser;
import com.fishmall.user.infrastructure.WeChatAuthClient;
import com.fishmall.user.repository.WxUserMapper;
import com.fishmall.user.security.UserJwtTokenService;
import com.fishmall.user.security.UserPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WxUserAuthService {
    private final WeChatAuthClient weChatAuthClient;
    private final WxUserMapper wxUserMapper;
    private final UserJwtTokenService tokenService;

    public WxUserAuthService(WeChatAuthClient weChatAuthClient,
                             WxUserMapper wxUserMapper,
                             UserJwtTokenService tokenService) {
        this.weChatAuthClient = weChatAuthClient;
        this.wxUserMapper = wxUserMapper;
        this.tokenService = tokenService;
    }

    @Transactional
    public UserTokenResponse login(WechatLoginRequest request) {
        String openid = weChatAuthClient.code2Session(request.code().trim());
        WxUser user = findOrCreate(openid);
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
        wxUserMapper.touchLogin(user.getId());
        UserJwtTokenService.TokenIssueResult result = tokenService.issue(new UserPrincipal(user.getId()));
        return new UserTokenResponse(result.token(), result.expiresIn());
    }

    @Transactional(readOnly = true)
    public UserInfoResponse current(UserPrincipal principal) {
        WxUser user = wxUserMapper.findById(principal.wxUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
        return new UserInfoResponse(user.getId(), user.getNickname(), user.getAvatarUrl());
    }

    private WxUser findOrCreate(String openid) {
        return wxUserMapper.findByOpenid(openid).orElseGet(() -> {
            WxUser created = new WxUser();
            created.setOpenid(openid);
            created.setStatus("ACTIVE");
            try {
                wxUserMapper.insert(created);
                return created;
            } catch (DuplicateKeyException exception) {
                return wxUserMapper.findByOpenid(openid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));
            }
        });
    }
}
