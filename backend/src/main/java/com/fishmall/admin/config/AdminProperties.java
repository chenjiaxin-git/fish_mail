package com.fishmall.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        String username,
        String initialPassword,
        String jwtSecret,
        long tokenTtlSeconds
) {
    public static final String DEFAULT_USERNAME = "admin";
    public static final long DEFAULT_TOKEN_TTL_SECONDS = 8L * 60 * 60;

    public AdminProperties {
        if (username == null || username.isBlank()) {
            username = DEFAULT_USERNAME;
        }
        if (tokenTtlSeconds <= 0) {
            tokenTtlSeconds = DEFAULT_TOKEN_TTL_SECONDS;
        }
    }
}
