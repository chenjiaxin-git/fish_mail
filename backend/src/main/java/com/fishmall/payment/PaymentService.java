package com.fishmall.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.repository.SalesOrderMapper;
import org.springframework.stereotype.Service;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentTransactions transactions;
    private final WeChatPayClient client;
    private final PaymentAudit audit;
    private final SalesOrderMapper orders;
    private final ObjectMapper json;

    public PaymentService(PaymentTransactions transactions, WeChatPayClient client, PaymentAudit audit,
                          SalesOrderMapper orders, ObjectMapper json) {
        this.transactions = transactions; this.client = client; this.audit = audit; this.orders = orders; this.json = json;
    }
    public record PrepayResponse(Long orderId, String orderNo, OffsetDateTime payDeadlineAt, Map<String, String> paymentParams) { }

    public PrepayResponse prepay(Long id, Long userId, int version) {
        // 先校验归属，再报告配置错误。
        var order = orders.findByUser(id, userId).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!client.available()) throw new BusinessException(ErrorCode.PAY_NOT_CONFIGURED);
        var claim = transactions.prepare(id, userId, version);
        var p = claim.payment();
        try {
            String prepayId = p.getPrepayId();
            if (!claim.firstSubmission()) {
                var remote = client.query(p.getOutTradeNo());
                if ("SUCCESS".equals(remote.state())) {
                    transactions.success(remote);
                    throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单已支付，请刷新");
                }
                if (!"NOT_EXISTS".equals(remote.state())) PaymentTransactions.validateIdentity(p, remote, false);
                if ("CLOSED".equals(remote.state())) {
                    transactions.finish(claim, "CLOSED", null);
                    throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "微信支付单已关闭，请取消此订单");
                }
                if (!"NOTPAY".equals(remote.state()) && !"NOT_EXISTS".equals(remote.state())) throw new BusinessException(ErrorCode.PAYMENT_PROCESSING);
            }
            if (prepayId == null) {
                prepayId = client.create(new WeChatPayClient.TradeRequest(p.getOutTradeNo(), p.getAmountCent(),
                        p.getPayerOpenid(), p.getExpiresAt().atZone(PaymentTransactions.ZONE).toOffsetDateTime()));
            }
            var params = client.sign(prepayId);
            transactions.finish(claim, "READY", prepayId);
            return new PrepayResponse(id, order.getOrderNo(), p.getExpiresAt().atZone(PaymentTransactions.ZONE).toOffsetDateTime(), params);
        } catch (RuntimeException e) {
            transactions.uncertain(claim); recordFailure("prepay", p.getOutTradeNo(), e); throw safe(e);
        }
    }

    public void sync(Long id, Long userId) { coordinate(id, userId, false); }
    public void prepareCancellation(Long id, Long userId) { coordinate(id, userId, true); }

    public boolean prepareTimeoutClosure(com.fishmall.order.domain.TimeoutObservation seen) {
        var result = transactions.claimTimeout(seen);
        if (!result.eligible()) return false;
        if (result.claim() != null) coordinateClaim(result.claim(), true);
        return true;
    }

    private void coordinate(Long id, Long userId, boolean cancel) {
        var claim = transactions.claimExisting(id, userId, cancel);
        if (claim == null) return;
        coordinateClaim(claim, cancel);
    }

    private void coordinateClaim(PaymentTransactions.Claim claim, boolean cancel) {
        try {
            var t = client.query(claim.payment().getOutTradeNo());
            if ("SUCCESS".equals(t.state())) { transactions.success(t); return; }
            if ("NOT_EXISTS".equals(t.state())) {
                // 曾提交过的支付单，查不到不代表不存在迟到创建请求，不能释放。
                throw new BusinessException(ErrorCode.PAYMENT_PROCESSING);
            }
            PaymentTransactions.validateIdentity(claim.payment(), t, false);
            if ("CLOSED".equals(t.state())) { transactions.finish(claim, "CLOSED", null); return; }
            if (cancel && "NOTPAY".equals(t.state())) {
                client.close(t.outTradeNo()); transactions.finish(claim, "CLOSED", null); return;
            }
            if (cancel) throw new BusinessException(ErrorCode.PAYMENT_PROCESSING);
            transactions.finish(claim, "READY", null);
        } catch (RuntimeException e) {
            transactions.uncertain(claim); recordFailure(cancel ? "close" : "sync", claim.payment().getOutTradeNo(), e); throw safe(e);
        }
    }

    public void notify(String body, Map<String, String> headers) {
        WeChatPayClient.Notification notification;
        try { notification = client.verify(body, headers); }
        catch (RuntimeException e) { recordFailure("verify", null, e); throw safe(e); }
        String encrypted;
        try {
            var envelope = json.readTree(body);
            encrypted = json.writeValueAsString(Map.of("resource", envelope.path("resource"),
                    "serial", headers.getOrDefault("Wechatpay-Serial", ""),
                    "timestamp", headers.getOrDefault("Wechatpay-Timestamp", ""),
                    "nonce", headers.getOrDefault("Wechatpay-Nonce", ""),
                    "signature", headers.getOrDefault("Wechatpay-Signature", "")));
        } catch (Exception e) { throw new BusinessException(ErrorCode.PAYMENT_NOTIFY_INVALID); }
        try {
            transactions.success(notification.trade());
            audit.record("notify:" + notification.eventId(), notification.trade().outTradeNo(), "SUCCESS", encrypted);
        } catch (RuntimeException e) {
            audit.record("notify:" + notification.eventId(), notification.trade().outTradeNo(), errorCode(e), encrypted);
            throw safe(e);
        }
    }
    private void recordFailure(String action, String no, RuntimeException e) {
        audit.record(action + ":" + UUID.randomUUID(), no, errorCode(e), null);
    }
    private String errorCode(RuntimeException e) { return e instanceof BusinessException b ? b.errorCode().code() : "PAYMENT_INTERNAL_ERROR"; }
    private RuntimeException safe(RuntimeException e) {
        return e instanceof BusinessException ? e : new BusinessException(ErrorCode.PAYMENT_REMOTE_ERROR);
    }
}
