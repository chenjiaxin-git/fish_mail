package com.fishmall.payment;

import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.config.WechatAuthProperties;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentConfigTest {
    @Test void defaultDisabledNeverFallsBackToFakeSuccess() {
        var p = new PayProperties(false, null, null, null, null, null, null, null, null, 0);
        var client = new PaymentConfig().weChatPayClient(p, new WechatAuthProperties(null, null));
        assertThat(client.available()).isFalse();
        assertThatThrownBy(() -> client.sign("anything")).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException)e).errorCode()).isEqualTo(ErrorCode.PAY_NOT_CONFIGURED);
    }
    @Test void enabledPaymentRequiresRealLoginAndSameAppId() {
        var p = new PayProperties(true, "wx-pay", null, null, null, null, null, null, null, 0);
        assertThatThrownBy(() -> new PaymentConfig().weChatPayClient(p, new WechatAuthProperties("wx-pay", "")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentConfig().weChatPayClient(p, new WechatAuthProperties("wx-other", "configured")))
                .isInstanceOf(IllegalStateException.class);
    }
}
