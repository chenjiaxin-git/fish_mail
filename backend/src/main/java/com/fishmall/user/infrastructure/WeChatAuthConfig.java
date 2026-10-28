package com.fishmall.user.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.user.config.WechatAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WeChatAuthConfig {
    private static final Logger log = LoggerFactory.getLogger(WeChatAuthConfig.class);

    @Bean
    public WeChatAuthClient weChatAuthClient(WechatAuthProperties properties, ObjectMapper objectMapper) {
        if (properties.appId() == null || properties.appId().isBlank()
                || properties.appSecret() == null || properties.appSecret().isBlank()) {
            log.warn("未配置微信 AppID/Secret，微信登录使用 Fake 模式，仅允许本地或测试环境使用");
            return new FakeWeChatAuthClient();
        }
        return new HttpWeChatAuthClient(properties, objectMapper);
    }
}
