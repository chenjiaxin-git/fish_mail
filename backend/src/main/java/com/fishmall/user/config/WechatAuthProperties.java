package com.fishmall.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat")
public record WechatAuthProperties(
        String appId,
        String appSecret
) {
}
