package com.fishmall.order.application;

import com.fishmall.order.application.service.*;
import com.fishmall.order.config.OrderProperties;
import com.fishmall.order.config.OrderTimeoutConfiguration;
import com.fishmall.order.domain.*;
import com.fishmall.order.repository.SalesOrderMapper;
import com.fishmall.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderTimeoutTest {
    final OrderProperties properties = new OrderProperties();
    final LocalDateTime now = LocalDateTime.of(2026,10,29,12,0);
    final OrderTimeoutPolicy policy = new OrderTimeoutPolicy(properties, Clock.fixed(now.atZone(OrderTimeoutPolicy.ZONE).toInstant(), ZoneOffset.UTC));

    @Test void createdDeadlineBoundaryUsesShanghaiEvenWithUtcClock() {
        var o = order(1L, "CREATED"); o.setCreatedAt(now.minusMinutes(1440));
        assertThat(policy.now()).isEqualTo(now); assertThat(policy.due(o)).isTrue();
        o.setCreatedAt(o.getCreatedAt().plusNanos(1)); assertThat(policy.due(o)).isFalse();
        o.setCreatedAt(now.minusMinutes(1441)); assertThat(policy.due(o)).isTrue();
        properties.setConfirmTimeoutMinutes(1500); assertThat(policy.due(o)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"QUOTED","PENDING_PAYMENT"})
    void savedPaymentDeadlineControlsExactBoundary(String status) {
        var o = order(1L,status); o.setPayDeadlineAt(now); assertThat(policy.due(o)).isTrue();
        properties.setPaymentTimeoutMinutes(200); assertThat(policy.due(o)).isTrue();
        o.setPayDeadlineAt(now.plusNanos(1)); assertThat(policy.due(o)).isFalse();
        o.setPayDeadlineAt(null); assertThat(policy.due(o)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"PAID_WAIT_DELIVERY","DELIVERING","COMPLETED","CANCELLED","CLOSED"})
    void paidAndTerminalOrdersAreNeverTimedOut(String status) {
        var o = order(1L,status); o.setCreatedAt(now.minusDays(10)); o.setPayDeadlineAt(now.minusDays(9));
        assertThat(policy.due(o)).isFalse();
        assertThatThrownBy(() -> OrderStateMachine.validate(o.status(), OrderAction.TIMEOUT_CLOSE)).isInstanceOf(RuntimeException.class);
    }
    @Test void retryBackoffCapsWithoutOverflow() {
        assertThat(policy.retrySeconds(1)).isEqualTo(30); assertThat(policy.retrySeconds(2)).isEqualTo(60);
        assertThat(policy.retrySeconds(7)).isEqualTo(1800); assertThat(policy.retrySeconds(Integer.MAX_VALUE)).isEqualTo(1800);
    }
    @Test void disabledScannerDoesNotReadDatabaseOrPayments() {
        var mapper = mock(SalesOrderMapper.class); var tx = mock(OrderTimeoutTransactions.class); var pay = mock(PaymentService.class);
        properties.setTimeoutCloseEnabled(false);
        new OrderTimeoutService(mapper,tx,pay,policy,properties).scan(); verifyNoInteractions(mapper,tx,pay);
    }
    @Test void scannerUsesCursorAndContinuesAfterAnOrderFailure() {
        var mapper = mock(SalesOrderMapper.class); var tx = mock(OrderTimeoutTransactions.class); var pay = mock(PaymentService.class);
        properties.setTimeoutCloseBatchSize(1); properties.setTimeoutCloseMaxBatches(2);
        when(mapper.timeoutCandidates(0L,now,now.minusMinutes(1440),1)).thenReturn(List.of(order(5L,"CREATED")));
        when(mapper.timeoutCandidates(5L,now,now.minusMinutes(1440),1)).thenReturn(List.of(order(8L,"CREATED")));
        when(tx.observe(5L)).thenThrow(new IllegalStateException("模拟单条数据库失败"));
        when(tx.observe(8L)).thenReturn(Optional.empty());
        new OrderTimeoutService(mapper,tx,pay,policy,properties).scan();
        verify(tx).observe(8L); verifyNoInteractions(pay);
        verify(mapper, times(2)).timeoutCandidates(anyLong(),any(),any(),eq(1));
    }
    @Test void scannerStopsBeforeStartingMoreOrdersWhenBudgetIsConsumed() {
        var mapper = mock(SalesOrderMapper.class); var tx = mock(OrderTimeoutTransactions.class); var pay = mock(PaymentService.class);
        properties.setTimeoutCloseBudgetMs(1);
        when(mapper.timeoutCandidates(anyLong(),any(),any(),anyInt())).thenAnswer(invocation -> {
            java.util.concurrent.locks.LockSupport.parkNanos(3_000_000);
            return List.of(order(1L,"CREATED"));
        });
        new OrderTimeoutService(mapper,tx,pay,policy,properties).scan(); verifyNoInteractions(tx,pay);
    }
    @Configuration @Import({OrderTimeoutJob.class,OrderTimeoutConfiguration.class}) static class JobConfig { }
    @Configuration @EnableConfigurationProperties(OrderProperties.class) static class PropertiesConfig { }
    @Test void jobDefaultsEnabledUsesFixedDelayAndDedicatedScheduler() throws Exception {
        new ApplicationContextRunner().withUserConfiguration(JobConfig.class)
                .withBean(OrderTimeoutService.class, () -> mock(OrderTimeoutService.class)).run(context -> {
                    assertThat(context).hasSingleBean(OrderTimeoutJob.class);
                    assertThat(context.getBean("orderTimeoutScheduler", ThreadPoolTaskScheduler.class).getPoolSize()).isEqualTo(1);
                    context.getBean(OrderTimeoutJob.class).run(); verify(context.getBean(OrderTimeoutService.class)).scan();
                });
        Scheduled schedule = OrderTimeoutJob.class.getMethod("run").getAnnotation(Scheduled.class);
        assertThat(schedule.fixedDelayString()).isEqualTo("${order.timeout-close-delay-ms:30000}");
        assertThat(schedule.initialDelayString()).isEqualTo(schedule.fixedDelayString());
        new ApplicationContextRunner().withUserConfiguration(JobConfig.class)
                .withBean(OrderTimeoutService.class, () -> mock(OrderTimeoutService.class))
                .withPropertyValues("order.timeout-close-enabled=false").run(context -> assertThat(context).doesNotHaveBean(OrderTimeoutJob.class));
    }
    @Test void invalidConfigurationFailsBinding() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("order.timeout-close-batch-size=101").run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("order.confirm-timeout-minutes=0").run(context -> assertThat(context).hasFailed());
        assertThat(properties.isTimeoutCloseEnabled()).isTrue();
    }
    private SalesOrder order(Long id, String status) {
        var o = new SalesOrder(); o.setId(id); o.setOrderNo("FM"+id); o.setStatus(status); o.setVersion(0L); return o;
    }
}
