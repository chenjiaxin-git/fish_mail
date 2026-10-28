package com.fishmall.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user")
public record UserSecurityProperties(
        String jwtSecret,
        long tokenTtlSeconds
) {
    public static final long DEFAULT_TOKEN_TTL_SECONDS = 7L * 24 * 60 * 60;

    public UserSecurityProperties {
        if (tokenTtlSeconds <= 0) {
            tokenTtlSeconds = DEFAULT_TOKEN_TTL_SECONDS;
        }
    }
}
