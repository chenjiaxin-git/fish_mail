package com.fishmall;

import com.fishmall.admin.config.AdminProperties;
import com.fishmall.user.config.UserSecurityProperties;
import com.fishmall.user.config.WechatAuthProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan({"com.fishmall.admin.repository", "com.fishmall.catalog.repository",
        "com.fishmall.order.repository", "com.fishmall.user.repository", "com.fishmall.cart.repository"})
@EnableConfigurationProperties({AdminProperties.class, com.fishmall.catalog.config.ProductStorageProperties.class,
        UserSecurityProperties.class, WechatAuthProperties.class})
public class FishMallApplication {
    public static void main(String[] args) {
        SpringApplication.run(FishMallApplication.class, args);
    }
}
