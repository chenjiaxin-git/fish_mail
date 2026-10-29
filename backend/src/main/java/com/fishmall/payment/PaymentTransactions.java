package com.fishmall.payment;

import com.fishmall.catalog.domain.ProductStockLedger;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.catalog.repository.ProductStockLedgerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.domain.*;
import com.fishmall.order.application.service.OrderTimeoutPolicy;
import com.fishmall.order.repository.*;
import com.fishmall.user.repository.WxUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 仅短事务；由 PaymentService 调用，不在持锁期间调用微信。 */
@Service
public class PaymentTransactions {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final SalesOrderMapper orders;
    private final SalesOrderItemMapper items;
    private final PaymentRecordMapper payments;
    private final ProductSpecMapper specs;
    private final ProductStockLedgerMapper ledger;
    private final OrderStatusLogMapper logs;
    private final WxUserMapper users;
    private final PayProperties properties;
    private final OrderTimeoutPolicy timeoutPolicy;

    public PaymentTransactions(SalesOrderMapper orders, SalesOrderItemMapper items, PaymentRecordMapper payments,
            ProductSpecMapper specs, ProductStockLedgerMapper ledger, OrderStatusLogMapper logs,
            WxUserMapper users, PayProperties properties, OrderTimeoutPolicy timeoutPolicy) {
        this.orders = orders; this.items = items; this.payments = payments; this.specs = specs;
        this.ledger = ledger; this.logs = logs; this.users = users; this.properties = properties;
        this.timeoutPolicy = timeoutPolicy;
    }

    public record Claim(PaymentRecord payment, String token, boolean firstSubmission) { }
    public record TimeoutClaim(boolean eligible, Claim claim) { }

    @Transactional
    public TimeoutClaim claimTimeout(TimeoutObservation seen) {
        SalesOrder order = lockOrder(seen.id(), null);
        if (!timeoutPolicy.matches(order, seen)) return new TimeoutClaim(false, null);
        var p = payments.lock(order.getId()).orElse(null);
        if (p == null || "SUCCESS".equals(p.getState()) || "CLOSED".equals(p.getPhase())) return new TimeoutClaim(true, null);
        return new TimeoutClaim(true, claim(p, "CLOSING"));
    }

    @Transactional
    public Claim prepare(Long id, Long userId, int quoteVersion) {
        SalesOrder order = lockOrder(id, userId);
        if (order.status() != OrderStatus.PENDING_PAYMENT) throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        if (!Objects.equals(order.getQuoteVersion(), quoteVersion)) throw new BusinessException(ErrorCode.QUOTE_VERSION_CONFLICT);
        if (order.getPayDeadlineAt() == null || !now().isBefore(order.getPayDeadlineAt())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "报价已过期，无法发起支付");
        }
        Long amount = order.getFinalAmountCent();
        if (amount == null || amount < 1 || amount > Integer.MAX_VALUE) throw new BusinessException(ErrorCode.PAY_AMOUNT_INVALID);
        var payment = payments.lock(id).orElse(null);
        if (payment == null) {
            var user = users.findById(userId).filter(u -> "ACTIVE".equals(u.getStatus()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
            if (user.getOpenid() == null || user.getOpenid().isBlank() || user.getOpenid().startsWith("fakeopenid_")) {
                throw new BusinessException(ErrorCode.PAYMENT_NOTIFY_INVALID, "请使用真实微信账号重新登录");
            }
            List<SalesOrderItem> lines = sortedItems(id);
            if (lines.isEmpty()) throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
            long sum = 0;
            for (SalesOrderItem item : lines) {
                BigDecimal actual = item.getActualQuantity();
                if (actual == null || actual.signum() < 0 || actual.scale() > 3
                        || ("PIECE".equals(item.getQuantityKind()) && actual.remainder(BigDecimal.ONE).signum() != 0)
                        || item.getFinalAmountCent() == null || item.getFinalAmountCent() < 0) {
                    throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单称重信息不完整");
                }
                sum = Math.addExact(sum, item.getFinalAmountCent());
                BigDecimal extra = actual.subtract(item.getEstimatedQuantity()).max(BigDecimal.ZERO);
                if (extra.signum() > 0) {
                    var spec = specs.findForUpdateById(item.getProductSpecId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND));
                    if (specs.reservePaymentExtra(spec.getId(), extra) != 1) throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
                    items.updatePaymentExtra(item.getId(), id, extra);
                    writeLedger(item, order, "RESERVE", extra, spec.getPhysicalStockQty(), spec.getPhysicalStockQty(),
                            spec.getReservedQty(), spec.getReservedQty().add(extra), "PAY_EXTRA_RESERVE", "支付前补足实称库存预占", "USER");
                }
            }
            if (sum != amount) throw new BusinessException(ErrorCode.ORDER_QUOTE_AMOUNT_MISMATCH);
            payment = new PaymentRecord();
            payment.setOrderId(id); payment.setOutTradeNo("FM" + Long.toUnsignedString(id, 36).toUpperCase(java.util.Locale.ROOT)); payment.setAmountCent(amount);
            payment.setQuoteVersion(quoteVersion); payment.setAppid(properties.appId()); payment.setMchid(properties.mchId());
            payment.setPayerOpenid(user.getOpenid()); payment.setExpiresAt(order.getPayDeadlineAt());
            payment.setPhase("NEW"); payment.setState("INIT"); payments.insert(payment);
        }
        if (!Objects.equals(payment.getAmountCent(), amount) || !Objects.equals(payment.getQuoteVersion(), quoteVersion)) {
            throw new BusinessException(ErrorCode.PAY_AMOUNT_MISMATCH);
        }
        if ("CLOSING".equals(payment.getPhase()) || "CLOSED".equals(payment.getPhase())) processing();
        return claim(payment, "CREATING");
    }

    @Transactional
    public Claim claimExisting(Long id, Long userId, boolean cancel) {
        lockOrder(id, userId);
        var p = payments.lock(id).orElse(null);
        if (p == null || "SUCCESS".equals(p.getState()) || "CLOSED".equals(p.getPhase())) return null;
        if (!cancel && p.getSyncAfter() != null && now().isBefore(p.getSyncAfter())) processing();
        if (!cancel && "CLOSING".equals(p.getPhase())) processing();
        return claim(p, cancel ? "CLOSING" : "SYNCING");
    }

    private Claim claim(PaymentRecord p, String phase) {
        if (p.getOperationToken() != null && p.getOperationUntil() != null && now().isBefore(p.getOperationUntil())) processing();
        boolean first = "NEW".equals(p.getPhase());
        String token = UUID.randomUUID().toString();
        p.setPhase(phase); p.setOperationToken(token); p.setOperationUntil(now().plusSeconds(120));
        p.setSyncAfter(now().plusSeconds(3)); payments.saveOperation(p);
        return new Claim(p, token, first);
    }

    @Transactional
    public void finish(Claim claim, String phase, String prepayId) {
        lockOrder(claim.payment().getOrderId(), null);
        PaymentRecord p = payments.lock(claim.payment().getOrderId()).orElseThrow();
        if ("SUCCESS".equals(p.getState())) throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单已支付，请刷新");
        if (!Objects.equals(p.getOperationToken(), claim.token())) processing();
        p.setPhase(phase); p.setPrepayId(prepayId == null ? p.getPrepayId() : prepayId);
        p.setOperationToken(null); p.setOperationUntil(null); payments.saveOperation(p);
    }

    @Transactional
    public void uncertain(Claim claim) {
        lockOrder(claim.payment().getOrderId(), null);
        PaymentRecord p = payments.lock(claim.payment().getOrderId()).orElseThrow();
        if (Objects.equals(p.getOperationToken(), claim.token()) && !"SUCCESS".equals(p.getState())) {
            p.setPhase("UNKNOWN"); p.setOperationToken(null); p.setOperationUntil(null); payments.saveOperation(p);
        }
    }

    @Transactional
    public void success(WeChatPayClient.Snapshot trade) {
        PaymentRecord located = payments.byTradeNo(trade.outTradeNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOTIFY_INVALID));
        SalesOrder order = lockOrder(located.getOrderId(), null);
        PaymentRecord p = payments.lock(order.getId()).orElseThrow();
        validateIdentity(p, trade, true);
        if ("SUCCESS".equals(p.getState())) {
            if (!Objects.equals(p.getTransactionId(), trade.transactionId())) throw new BusinessException(ErrorCode.PAYMENT_NOTIFY_INVALID);
            return;
        }
        OrderStateMachine.validate(order.status(), OrderAction.PAYMENT_SUCCESS);
        if (!Objects.equals(order.getFinalAmountCent(), p.getAmountCent())
                || !Objects.equals(order.getQuoteVersion(), p.getQuoteVersion())) throw new BusinessException(ErrorCode.PAY_AMOUNT_MISMATCH);
        Instant paid = trade.successTime().toInstant();
        if (paid.isAfter(Instant.now().plusSeconds(300)) || p.getExpiresAt() == null
                || paid.isAfter(p.getExpiresAt().atZone(ZONE).toInstant())
                || (order.getCreatedAt() != null && paid.isBefore(order.getCreatedAt().atZone(ZONE).toInstant().minusSeconds(1)))) {
            throw new BusinessException(ErrorCode.PAYMENT_NOTIFY_INVALID);
        }
        for (SalesOrderItem item : sortedItems(order.getId())) {
            var spec = specs.findForUpdateById(item.getProductSpecId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND));
            if (item.getActualQuantity() == null || item.getActualQuantity().signum() < 0
                    || specs.deductPaid(spec.getId(), item.getActualQuantity(), item.reservedQuantity()) != 1) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
            }
            writeLedger(item, order, "PAY_DEDUCT", item.getActualQuantity().negate(), spec.getPhysicalStockQty(),
                    spec.getPhysicalStockQty().subtract(item.getActualQuantity()), spec.getReservedQty(),
                    spec.getReservedQty().subtract(item.reservedQuantity()), "PAY_DEDUCT", "微信支付成功", "PAYMENT");
        }
        LocalDateTime paidAt = LocalDateTime.ofInstant(paid, ZONE);
        p.setTransactionId(trade.transactionId()); p.setPaidAt(paidAt); payments.markSuccess(p);
        if (orders.markPaid(order.getId(), order.getVersion(), paidAt) != 1) throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        OrderStatusLog log = new OrderStatusLog(); log.setOrderId(order.getId()); log.setFromStatus(order.getStatus());
        log.setToStatus("PAID_WAIT_DELIVERY"); log.setOperatorType("PAYMENT"); log.setSource("WECHAT_PAY");
        log.setReason("微信支付成功"); logs.insert(log);
    }

    public static void validateIdentity(PaymentRecord p, WeChatPayClient.Snapshot t, boolean success) {
        if (!Objects.equals(p.getOutTradeNo(), t.outTradeNo()) || !Objects.equals(p.getAppid(), t.appid())
                || !Objects.equals(p.getMchid(), t.mchid())) throw new BusinessException(ErrorCode.PAYMENT_NOTIFY_INVALID);
        if ((success || t.total() != null) && !Objects.equals(p.getAmountCent(), t.total())) throw new BusinessException(ErrorCode.PAY_AMOUNT_MISMATCH);
        if (success && (!"SUCCESS".equals(t.state()) || !"CNY".equals(t.currency()) || !"JSAPI".equals(t.tradeType())
                || !Objects.equals(p.getPayerOpenid(), t.openid()) || t.transactionId() == null || t.transactionId().isBlank()
                || t.transactionId().length() > 64 || t.successTime() == null)) throw new BusinessException(ErrorCode.PAYMENT_NOTIFY_INVALID);
    }

    private SalesOrder lockOrder(Long id, Long userId) {
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return (userId == null ? orders.findByIdForUpdate(id) : orders.findByUserForUpdate(id, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
    private List<SalesOrderItem> sortedItems(Long id) {
        return items.findByOrderId(id).stream().sorted(Comparator.comparing(SalesOrderItem::getProductSpecId)).toList();
    }
    private void writeLedger(SalesOrderItem i, SalesOrder o, String type, BigDecimal quantity,
            BigDecimal physicalBefore, BigDecimal physicalAfter, BigDecimal reservedBefore, BigDecimal reservedAfter,
            String key, String reason, String operator) {
        ProductStockLedger l = new ProductStockLedger(); l.setOrderId(o.getId()); l.setProductSpecId(i.getProductSpecId());
        l.setChangeType(type); l.setQuantity(quantity); l.setPhysicalBefore(physicalBefore); l.setPhysicalAfter(physicalAfter);
        l.setReservedBefore(reservedBefore); l.setReservedAfter(reservedAfter); l.setBizKey(key + ":" + o.getId() + ":" + i.getProductSpecId());
        l.setReason(reason); l.setOperatorType(operator); l.setOperatorId("USER".equals(operator) ? o.getWxUserId() : null); ledger.insert(l);
    }
    private LocalDateTime now() { return LocalDateTime.now(ZONE); }
    private void processing() { throw new BusinessException(ErrorCode.PAYMENT_PROCESSING); }
}
