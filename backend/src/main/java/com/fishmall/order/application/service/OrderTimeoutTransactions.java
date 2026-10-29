package com.fishmall.order.application.service;

import com.fishmall.catalog.domain.ProductStockLedger;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.catalog.repository.ProductStockLedgerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.domain.*;
import com.fishmall.order.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

@Service
public class OrderTimeoutTransactions {
    private final SalesOrderMapper orders;
    private final PaymentRecordMapper payments;
    private final SalesOrderItemMapper items;
    private final ProductSpecMapper specs;
    private final ProductStockLedgerMapper ledger;
    private final OrderStatusLogMapper logs;
    private final OrderTimeoutPolicy policy;

    public OrderTimeoutTransactions(SalesOrderMapper orders, PaymentRecordMapper payments, SalesOrderItemMapper items,
            ProductSpecMapper specs, ProductStockLedgerMapper ledger, OrderStatusLogMapper logs, OrderTimeoutPolicy policy) {
        this.orders = orders; this.payments = payments; this.items = items; this.specs = specs;
        this.ledger = ledger; this.logs = logs; this.policy = policy;
    }

    @Transactional
    public Optional<TimeoutObservation> observe(Long id) {
        return orders.findByIdForUpdate(id).filter(policy::due)
                .map(o -> new TimeoutObservation(o.getId(), o.getOrderNo(), o.getVersion(), o.status(), policy.deadline(o)));
    }

    @Transactional
    public boolean close(TimeoutObservation seen) {
        SalesOrder order = orders.findByIdForUpdate(seen.id()).orElse(null);
        if (order == null || !policy.matches(order, seen)) return false;
        var payment = payments.lock(order.getId()).orElse(null);
        if (payment != null && ("SUCCESS".equals(payment.getState()) || !"CLOSED".equals(payment.getPhase()))) {
            throw new BusinessException(ErrorCode.PAYMENT_PROCESSING);
        }
        OrderStateMachine.validate(order.status(), OrderAction.TIMEOUT_CLOSE);
        var now = policy.now();
        if (orders.closeTimedOut(order.getId(), order.getVersion(), order.getStatus(), now, policy.createdCutoff(now)) != 1) return false;
        var lines = items.findByOrderId(order.getId());
        if (lines.isEmpty()) throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "超时订单缺少商品项");
        String reason = order.status() == OrderStatus.CREATED ? "商家确认超时自动关闭" : "报价/支付超时自动关闭";
        for (SalesOrderItem item : lines.stream().sorted(Comparator.comparing(SalesOrderItem::getProductSpecId)).toList()) {
            var spec = specs.findForUpdateById(item.getProductSpecId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND));
            var quantity = item.reservedQuantity();
            if (specs.releaseReserved(spec.getId(), quantity) != 1) throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
            var entry = new ProductStockLedger(); entry.setOrderId(order.getId()); entry.setProductSpecId(spec.getId());
            entry.setChangeType("RELEASE"); entry.setQuantity(quantity.negate());
            entry.setPhysicalBefore(spec.getPhysicalStockQty()); entry.setPhysicalAfter(spec.getPhysicalStockQty());
            entry.setReservedBefore(spec.getReservedQty()); entry.setReservedAfter(spec.getReservedQty().subtract(quantity));
            entry.setReason(reason); entry.setOperatorType("SYSTEM");
            entry.setBizKey("RELEASE:" + order.getId() + ":" + spec.getId()); ledger.insert(entry);
        }
        var log = new OrderStatusLog(); log.setOrderId(order.getId()); log.setFromStatus(order.getStatus());
        log.setToStatus("CLOSED"); log.setOperatorType("SYSTEM"); log.setSource("TIMEOUT_JOB"); log.setReason(reason); logs.insert(log);
        return true;
    }

    public record Retry(int attempts, LocalDateTime next) { }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Retry> retry(TimeoutObservation seen) {
        SalesOrder order = orders.findByIdForUpdate(seen.id()).orElse(null);
        if (order == null || !policy.matches(order, seen)) return Optional.empty();
        int attempts = order.getTimeoutAttemptCount() >= 32 ? 32 : order.getTimeoutAttemptCount() + 1;
        var now = policy.now(); var next = now.plusSeconds(policy.retrySeconds(attempts));
        if (orders.retryTimeout(order.getId(), seen.version(), order.getStatus(), now, policy.createdCutoff(now), next) != 1) return Optional.empty();
        return Optional.of(new Retry(attempts, next));
    }
}
