package com.fishmall.payment;

import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.config.WechatAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(PayProperties.class)
public class PaymentConfig {
    @Bean
    public WeChatPayClient weChatPayClient(PayProperties properties, WechatAuthProperties login) {
        if (properties.enabled()) {
            if (login.appSecret() == null || login.appSecret().isBlank() || !java.util.Objects.equals(properties.appId(), login.appId())) {
                throw new IllegalStateException("真实支付要求配置真实微信登录，不能使用 Fake 登录");
            }
            return new SdkWeChatPayClient(properties);
        }
        return new WeChatPayClient() {
            private BusinessException disabled() { return new BusinessException(ErrorCode.PAY_NOT_CONFIGURED); }
            public boolean available() { return false; }
            public String create(TradeRequest r) { throw disabled(); }
            public Snapshot query(String no) { throw disabled(); }
            public void close(String no) { throw disabled(); }
            public Map<String, String> sign(String id) { throw disabled(); }
            public Notification verify(String body, Map<String, String> headers) { throw disabled(); }
        };
    }
}
