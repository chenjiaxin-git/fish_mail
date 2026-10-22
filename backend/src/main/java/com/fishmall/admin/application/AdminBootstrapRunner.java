package com.fishmall.admin.application;

import com.fishmall.admin.config.AdminProperties;
import com.fishmall.admin.domain.AdminUser;
import com.fishmall.admin.repository.AdminUserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminProperties properties;
    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(
            AdminProperties properties,
            AdminUserMapper adminUserMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.jwtSecret() == null || properties.jwtSecret().getBytes().length < 32) {
            throw new IllegalStateException("JWT_SECRET 必须为至少32字节的随机值");
        }
        if (adminUserMapper.count() == 0) {
            if (properties.initialPassword() == null || properties.initialPassword().length() < 8) {
                throw new IllegalStateException("ADMIN_INITIAL_PASSWORD 必须为至少8位密码");
            }
            AdminUser admin = new AdminUser();
            admin.setId(1L);
            admin.setUsername(properties.username());
            admin.setPasswordHash(passwordEncoder.encode(properties.initialPassword()));
            admin.setTokenVersion(0L);
            admin.setStatus("ACTIVE");
            adminUserMapper.insert(admin);
            return;
        }
        AdminUser admin = adminUserMapper.findById(1L)
                .orElseThrow(() -> new IllegalStateException("唯一管理员数据异常"));
        if (!properties.username().equals(admin.getUsername())) {
            throw new IllegalStateException("ADMIN_USERNAME 与已有管理员不一致");
        }
    }
}
