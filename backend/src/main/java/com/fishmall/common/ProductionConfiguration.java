package com.fishmall.common;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Configuration
@Profile("prod")
public class ProductionConfiguration {
    @Bean
    public InitializingBean productionValidation(Environment env) {
        return () -> {
            for (String key : List.of("admin.jwt-secret", "user.jwt-secret")) {
                String value = env.getProperty(key, "");
                if (value.getBytes(StandardCharsets.UTF_8).length < 32
                        || value.matches("(?i).*(change|example|0123456789|replace|test-only).*"))
                    throw new IllegalStateException("生产JWT密钥必须独立生成：" + key);
            }
            if (Objects.equals(
                    env.getProperty("admin.jwt-secret"), env.getProperty("user.jwt-secret")))
                throw new IllegalStateException("管理员与用户JWT密钥必须不同");
            for (String key :
                    List.of("spring.datasource.password", "wechat.app-id", "wechat.app-secret"))
                if (env.getProperty(key, "").isBlank())
                    throw new IllegalStateException("生产配置缺失：" + key);
            if (!env.getProperty("wechat.app-id", "").matches("wx[0-9a-fA-F]{16}"))
                throw new IllegalStateException("生产微信AppID格式不合法，禁止游客模式");
            if (!"minio".equals(env.getProperty("storage.type")))
                throw new IllegalStateException("生产交付配置必须使用MinIO存储");
        };
    }
}
