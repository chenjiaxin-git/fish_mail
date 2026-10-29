package com.fishmall.payment;

import java.time.OffsetDateTime;
import java.util.Map;

/** 微信支付边界。实现保证所有返回交易来自验签通过的微信响应或通知。 */
public interface WeChatPayClient {
    boolean available();
    String create(TradeRequest request);
    Snapshot query(String outTradeNo);
    void close(String outTradeNo);
    Map<String, String> sign(String prepayId);
    Notification verify(String body, Map<String, String> headers);

    record TradeRequest(String outTradeNo, long amountCent, String openid, OffsetDateTime expiresAt) { }
    record Snapshot(String outTradeNo, String transactionId, String state, String appid, String mchid,
                    String openid, Long total, String currency, String tradeType, OffsetDateTime successTime) { }
    record Notification(String eventId, String eventType, Snapshot trade) { }
}
