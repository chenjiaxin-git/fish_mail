package com.fishmall.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public class SdkWeChatPayClient implements WeChatPayClient {
    private final PayProperties properties;
    private final RSAPublicKeyConfig config;
    private final JsapiService service;
    private final NotificationParser parser;
    private final ObjectMapper json = new ObjectMapper();

    public SdkWeChatPayClient(PayProperties p) {
        for (String value : new String[]{p.appId(), p.mchId(), p.merchantSerialNo(), p.privateKeyPath(),
                p.publicKeyId(), p.publicKeyPath(), p.apiV3Key(), p.notifyUrl()}) {
            if (value == null || value.isBlank()) throw new IllegalStateException("微信支付启用时必须填写完整公钥模式配置");
        }
        URI notify = URI.create(p.notifyUrl());
        if (!"https".equals(notify.getScheme()) || notify.getHost() == null || notify.getUserInfo() != null
                || p.apiV3Key().getBytes(StandardCharsets.UTF_8).length != 32 || !p.publicKeyId().startsWith("PUB_KEY_ID_")
                || p.timeoutMs() > 15000) throw new IllegalStateException("微信支付 HTTPS、公钥标识、密钥长度或超时配置无效");
        properties = p;
        config = new RSAPublicKeyConfig.Builder().merchantId(p.mchId()).merchantSerialNumber(p.merchantSerialNo())
                .privateKeyFromPath(p.privateKeyPath()).publicKeyFromPath(p.publicKeyPath())
                .publicKeyId(p.publicKeyId()).apiV3Key(p.apiV3Key()).build();
        var http = new DefaultHttpClientBuilder().config(config).connectTimeoutMs(p.timeoutMs())
                .readTimeoutMs(p.timeoutMs()).writeTimeoutMs(p.timeoutMs()).disableRetryOnConnectionFailure().build();
        service = new JsapiService.Builder().httpClient(http).build();
        parser = new NotificationParser(config);
    }

    public boolean available() { return true; }

    public String create(TradeRequest trade) {
        try {
            PrepayRequest r = new PrepayRequest();
            r.setAppid(properties.appId()); r.setMchid(properties.mchId()); r.setDescription("生鲜水产订单");
            r.setOutTradeNo(trade.outTradeNo()); r.setNotifyUrl(properties.notifyUrl());
            r.setTimeExpire(trade.expiresAt().toString());
            Amount amount = new Amount(); amount.setTotal(Math.toIntExact(trade.amountCent())); amount.setCurrency("CNY"); r.setAmount(amount);
            Payer payer = new Payer(); payer.setOpenid(trade.openid()); r.setPayer(payer);
            String id = service.prepay(r).getPrepayId();
            if (id == null || id.isBlank()) throw new IllegalStateException("微信预支付响应缺少标识");
            return id;
        } catch (RuntimeException e) { throw remote(); }
    }

    public Snapshot query(String no) {
        try {
            var r = new QueryOrderByOutTradeNoRequest(); r.setMchid(properties.mchId()); r.setOutTradeNo(no);
            return snapshot(service.queryOrderByOutTradeNo(r));
        } catch (ServiceException e) {
            if ("ORDER_NOT_EXIST".equals(e.getErrorCode()) || "ORDER_NOT_EXISTS".equals(e.getErrorCode()) || "ORDERNOTEXIST".equals(e.getErrorCode())) {
                return new Snapshot(no, null, "NOT_EXISTS", null, null, null, null, null, null, null);
            }
            throw remote();
        } catch (RuntimeException e) { throw remote(); }
    }

    public void close(String no) {
        try {
            var r = new CloseOrderRequest(); r.setMchid(properties.mchId()); r.setOutTradeNo(no); service.closeOrder(r);
        } catch (RuntimeException e) { throw remote(); }
    }

    public Map<String, String> sign(String id) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String pkg = "prepay_id=" + id;
        String signature = config.createSigner().sign(properties.appId() + "\n" + timestamp + "\n" + nonce + "\n" + pkg + "\n").getSign();
        return Map.of("timeStamp", timestamp, "nonceStr", nonce, "package", pkg, "signType", "RSA", "paySign", signature);
    }

    public Notification verify(String body, Map<String, String> headers) {
        try {
            long stamp = Long.parseLong(headers.get("Wechatpay-Timestamp"));
            if (stamp < Instant.now().minusSeconds(300).getEpochSecond() || stamp > Instant.now().plusSeconds(300).getEpochSecond()
                    || body.length() > 65536 || headers.get("Wechatpay-Nonce") == null || headers.get("Wechatpay-Nonce").isBlank()) {
                throw new IllegalArgumentException();
            }
            RequestParam r = new RequestParam.Builder().serialNumber(headers.get("Wechatpay-Serial"))
                    .signature(headers.get("Wechatpay-Signature")).timestamp(headers.get("Wechatpay-Timestamp"))
                    .nonce(headers.get("Wechatpay-Nonce")).signType(headers.get("Wechatpay-Signature-Type")).body(body).build();
            Transaction t = parser.parse(r, Transaction.class);
            var envelope = json.readTree(body);
            String eventId = envelope.path("id").asText(); String type = envelope.path("event_type").asText();
            if (eventId.isBlank() || eventId.length() > 80 || !"TRANSACTION.SUCCESS".equals(type)) throw new IllegalArgumentException();
            return new Notification(eventId, type, snapshot(t));
        } catch (Exception e) {
            // SDK 异常可能含通知原文，不将异常或其 cause 传播到通用日志。
            throw new BusinessException(ErrorCode.PAYMENT_NOTIFY_INVALID);
        }
    }

    private Snapshot snapshot(Transaction t) {
        if (t == null || t.getTradeState() == null) throw remote();
        return new Snapshot(t.getOutTradeNo(), t.getTransactionId(), t.getTradeState().name(), t.getAppid(), t.getMchid(),
                t.getPayer() == null ? null : t.getPayer().getOpenid(),
                t.getAmount() == null || t.getAmount().getTotal() == null ? null : t.getAmount().getTotal().longValue(),
                t.getAmount() == null ? null : t.getAmount().getCurrency(),
                t.getTradeType() == null ? null : t.getTradeType().name(),
                t.getSuccessTime() == null ? null : OffsetDateTime.parse(t.getSuccessTime()));
    }
    private BusinessException remote() { return new BusinessException(ErrorCode.PAYMENT_REMOTE_ERROR); }
}
