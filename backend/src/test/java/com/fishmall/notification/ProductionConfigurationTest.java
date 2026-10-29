package com.fishmall.notification;

import static org.assertj.core.api.Assertions.*;

import com.fishmall.common.ProductionConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionConfigurationTest {
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(ProductionConfiguration.class)
                .withPropertyValues(
                        "admin.jwt-secret=admin-production-key-only-for-unit-validation-123",
                        "user.jwt-secret=user-production-key-only-for-unit-validation-456",
                        "spring.datasource.password=unit-validation-password",
                        "wechat.app-id=wx0123456789abcdef",
                        "wechat.app-secret=unit-validation-secret",
                        "storage.type=minio");
    }

    @Test
    void acceptsCompleteProductionConfiguration() {
        context().run(c -> assertThat(c).hasNotFailed());
    }

    @Test
    void refusesEmptyWechatAndTouristMode() {
        context().withPropertyValues("wechat.app-secret=").run(c -> assertThat(c).hasFailed());
        context()
                .withPropertyValues("wechat.app-id=touristappid")
                .run(c -> assertThat(c).hasFailed());
    }

    @Test
    void refusesSharedOrExampleJwtSecrets() {
        context()
                .withPropertyValues("admin.jwt-secret=01234567890123456789012345678901")
                .run(c -> assertThat(c).hasFailed());
        context()
                .withPropertyValues(
                        "admin.jwt-secret=equal-production-secret-with-at-least-32-bytes",
                        "user.jwt-secret=equal-production-secret-with-at-least-32-bytes")
                .run(c -> assertThat(c).hasFailed());
    }

    @Test
    void refusesAccidentalLocalStorageInProduction() {
        context().withPropertyValues("storage.type=local").run(c -> assertThat(c).hasFailed());
    }
}
