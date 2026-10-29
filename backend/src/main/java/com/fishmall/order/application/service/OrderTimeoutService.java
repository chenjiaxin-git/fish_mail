package com.fishmall.order.application.service;

import com.fishmall.common.BusinessException;
import com.fishmall.order.config.OrderProperties;
import com.fishmall.order.repository.SalesOrderMapper;
import com.fishmall.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OrderTimeoutService {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);
    private final SalesOrderMapper orders;
    private final OrderTimeoutTransactions transactions;
    private final PaymentService payments;
    private final OrderTimeoutPolicy policy;
    private final OrderProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();
    public OrderTimeoutService(SalesOrderMapper orders, OrderTimeoutTransactions transactions, PaymentService payments,
            OrderTimeoutPolicy policy, OrderProperties properties) {
        this.orders = orders; this.transactions = transactions; this.payments = payments; this.policy = policy; this.properties = properties;
    }

    public boolean process(Long id) {
        var observed = transactions.observe(id);
        if (observed.isEmpty()) return false;
        var seen = observed.get(); String traceId = UUID.randomUUID().toString();
        try {
            if (!payments.prepareTimeoutClosure(seen)) return false;
            boolean closed = transactions.close(seen);
            log.info("订单超时处理, traceId={}, orderNo={}, result={}", traceId, seen.orderNo(), closed ? "CLOSED" : "SKIPPED");
            return closed;
        } catch (RuntimeException e) {
            String code = e instanceof BusinessException b ? b.errorCode().code() : e.getClass().getSimpleName();
            var retry = transactions.retry(seen);
            if (retry.isPresent()) log.warn("超时关闭待重试/人工核查, traceId={}, orderNo={}, error={}, attempts={}, next={}",
                    traceId, seen.orderNo(), code, retry.get().attempts(), retry.get().next());
            return false;
        }
    }

    public void scan() {
        if (!properties.isTimeoutCloseEnabled() || !running.compareAndSet(false, true)) return;
        long started = System.nanoTime(); long afterId = 0;
        try {
            var scanTime = policy.now();
            for (int batch = 0; batch < properties.getTimeoutCloseMaxBatches(); batch++) {
                var candidates = orders.timeoutCandidates(afterId, scanTime, policy.createdCutoff(scanTime), properties.getTimeoutCloseBatchSize());
                if (candidates.isEmpty()) break;
                for (var order : candidates) {
                    if ((System.nanoTime() - started) / 1_000_000 >= properties.getTimeoutCloseBudgetMs()) return;
                    afterId = order.getId();
                    try { process(order.getId()); }
                    catch (RuntimeException e) {
                        // 包含重试元数据写入失败，不能伪报已重试；继续后面的候选，下一轮仍会扫描本单。
                        log.error("超时关闭或重试信息写入失败, orderNo={}, error={}", order.getOrderNo(), e.getClass().getSimpleName());
                    }
                }
                if (candidates.size() < properties.getTimeoutCloseBatchSize()) break;
            }
        } catch (RuntimeException e) {
            log.error("超时订单扫描失败, error={}", e.getClass().getSimpleName());
        } finally { running.set(false); }
    }
}
