package com.fishmall.payment;

import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.api.param.CancelOrderRequest;
import com.fishmall.order.api.param.RefundConfirmRequest;
import com.fishmall.order.api.param.QuoteRequest;
import com.fishmall.order.api.param.QuoteItemRequest;
import com.fishmall.order.application.service.AdminOrderService;
import com.fishmall.order.application.service.UserOrderService;
import com.fishmall.order.repository.PaymentRecordMapper;
import com.fishmall.order.config.OrderProperties;
import com.fishmall.order.application.service.OrderTimeoutService;
import com.fishmall.order.application.service.OrderTimeoutTransactions;
import com.fishmall.order.repository.SalesOrderMapper;
import com.fishmall.user.security.UserJwtTokenService;
import com.fishmall.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fishmall").withUsername("test").withPassword("test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl); r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("admin.jwt-secret", () -> "01234567890123456789012345678901");
        r.add("user.jwt-secret", () -> "01234567890123456789012345678901");
        r.add("admin.initial-password", () -> "initial-pass-123");
        r.add("wechat-pay.app-id", () -> "wx-test"); r.add("wechat-pay.mch-id", () -> "mch-test");
    }
    @TestConfiguration static class Config {
        @Bean @Primary FakePay fakePay() { return new FakePay(); }
        @Bean @Primary MutableClock timeoutTestClock() { return new MutableClock(); }
    }
    @Autowired JdbcTemplate db;
    @Autowired PaymentService payments;
    @Autowired PaymentRecordMapper records;
    @Autowired UserOrderService orders;
    @Autowired AdminOrderService admin;
    @Autowired FakePay fake;
    @Autowired UserJwtTokenService tokens;
    @Autowired MockMvc mvc;
    @Autowired OrderTimeoutService timeout;
    @Autowired OrderTimeoutTransactions timeoutTx;
    @Autowired SalesOrderMapper orderMapper;
    @Autowired OrderProperties orderProperties;
    @Autowired MutableClock timeoutClock;
    Long id, spec, userId;
    String openid;

    @BeforeEach void fixture() {
        timeoutClock.current = Instant.now();
        orderProperties.setTimeoutCloseEnabled(false); orderProperties.setConfirmTimeoutMinutes(1440);
        orderProperties.setPaymentTimeoutMinutes(60);
        orderProperties.setTimeoutCloseBatchSize(50); orderProperties.setTimeoutCloseMaxBatches(10);
        orderProperties.setTimeoutCloseBudgetMs(20000);
        fake.reset();
        String name = UUID.randomUUID().toString(); openid = "openid-" + name;
        db.update("insert into wx_user(openid) values(?)", openid);
        userId = db.queryForObject("select id from wx_user where openid=?", Long.class, openid);
        db.update("insert into product_category(name) values(?)", name);
        Long category = db.queryForObject("select id from product_category where name=?", Long.class, name);
        db.update("insert into product(category_id,name,status) values(?,?,'ACTIVE')", category, name);
        Long product = db.queryForObject("select id from product where category_id=?", Long.class, category);
        db.update("insert into product_spec(product_id,name,quantity_kind,unit,price_cent,physical_stock_qty,reserved_qty) "
                + "values(?,'称重','WEIGHT','kg',3000,5,1.5)", product);
        spec = db.queryForObject("select id from product_spec where product_id=?", Long.class, product);
        db.update("insert into sales_order(order_no,wx_user_id,status,address_snapshot,estimated_amount_cent,final_amount_cent,quote_version,"
                + "quoted_at,pay_deadline_at) values(?,?,'PENDING_PAYMENT',?,4500,3600,1,current_timestamp,date_add(current_timestamp,interval 60 minute))",
                name, userId, "{\"contactName\":\"张三\",\"phone\":\"13800138000\",\"detailAddress\":\"渔港路\"}");
        id = db.queryForObject("select id from sales_order where order_no=?", Long.class, name);
        db.update("insert into sales_order_item(order_id,product_id,product_spec_id,product_name,spec_name,quantity_kind,unit,unit_price_cent,"
                + "estimated_quantity,actual_quantity,estimated_amount_cent,final_amount_cent) values(?,?,?,'草鱼','称重','WEIGHT','kg',3000,1.5,1.2,4500,3600)",
                id, product, spec);
    }
    UserPrincipal user() { return new UserPrincipal(userId); }
    WeChatPayClient.Snapshot success() {
        var p = records.current(id).orElseThrow();
        return new WeChatPayClient.Snapshot(p.getOutTradeNo(), "tx-" + id, "SUCCESS", "wx-test", "mch-test", openid,
                p.getAmountCent(), "CNY", "JSAPI", OffsetDateTime.now(PaymentTransactions.ZONE));
    }
    void notifySuccess(WeChatPayClient.Snapshot t) {
        fake.notification = new WeChatPayClient.Notification("event-" + t.outTradeNo(), "TRANSACTION.SUCCESS", t);
        payments.notify("{\"resource\":{\"ciphertext\":\"encrypted\"}}", Map.of());
    }
    @Test void smallerWeightAndConcurrentDuplicateNotificationsDeductOnlyOnce() throws Exception {
        payments.prepay(id, userId, 1);
        fake.notification = new WeChatPayClient.Notification("event-small", "TRANSACTION.SUCCESS", success());
        race(() -> payments.notify("{\"resource\":{}}", Map.of()), () -> payments.notify("{\"resource\":{}}", Map.of()));
        assertStock("3.800", "0.000"); assertThat(orders.detail(user(), id).status()).isEqualTo("PAID_WAIT_DELIVERY");
        assertThat(count("product_stock_ledger", "change_type='PAY_DEDUCT'")).isEqualTo(1);
        assertThat(count("order_status_log", "to_status='PAID_WAIT_DELIVERY'")).isEqualTo(1);
        assertThat(records.current(id).orElseThrow().getState()).isEqualTo("SUCCESS");
    }
    @Test void extraWeightReservedOnceAndReleasedOnClosedCancellation() {
        db.update("update sales_order_item set actual_quantity=1.8 where order_id=?", id);
        var first = payments.prepay(id, userId, 1); var second = payments.prepay(id, userId, 1);
        assertThat(first.orderId()).isEqualTo(second.orderId()); assertThat(fake.creates).isEqualTo(1);
        assertStock("5.000", "1.800");
        assertThat(count("product_stock_ledger", "change_type='RESERVE'")).isEqualTo(1);
        assertError(() -> orders.cancel(user(), id, new CancelOrderRequest("取消")), ErrorCode.PAYMENT_PROCESSING);
        payments.prepareCancellation(id, userId);
        orders.cancel(user(), id, new CancelOrderRequest("取消"));
        assertThat(fake.closes).isEqualTo(1); assertStock("5.000", "0.000");
        assertError(() -> payments.prepay(id, userId, 1), ErrorCode.ORDER_STATE_INVALID);
    }
    @Test void largerWeightDeductsActualAndAllReservations() {
        db.update("update sales_order_item set actual_quantity=1.8 where order_id=?", id);
        payments.prepay(id, userId, 1); notifySuccess(success()); assertStock("3.200", "0.000");
    }
    @Test void insufficientExtraStockRollsBackBeforeRemoteRequest() {
        db.update("update product_spec set physical_stock_qty=1.5 where id=?", spec);
        db.update("update sales_order_item set actual_quantity=1.8 where order_id=?", id);
        assertError(() -> payments.prepay(id, userId, 1), ErrorCode.STOCK_INSUFFICIENT);
        assertThat(records.current(id)).isEmpty(); assertThat(fake.creates).isZero(); assertStock("1.500", "1.500");
    }
    @Test void zeroAmountAndUnavailableConfigurationNeverCallWechat() {
        db.update("update sales_order set final_amount_cent=0 where id=?", id);
        assertError(() -> payments.prepay(id, userId, 1), ErrorCode.PAY_AMOUNT_INVALID);
        fake.enabled = false;
        assertError(() -> payments.prepay(id, userId, 1), ErrorCode.PAY_NOT_CONFIGURED);
        assertThat(records.current(id)).isEmpty(); assertThat(fake.creates).isZero();
    }
    @Test void amountVersionExpiryAndFakePayerAreValidated() {
        assertError(() -> payments.prepay(id, userId, 2), ErrorCode.QUOTE_VERSION_CONFLICT);
        db.update("update wx_user set openid='fakeopenid_example' where id=?", userId);
        assertError(() -> payments.prepay(id, userId, 1), ErrorCode.PAYMENT_NOTIFY_INVALID);
        db.update("update sales_order set pay_deadline_at=current_timestamp(3) where id=?", id);
        assertError(() -> payments.prepay(id, userId, 1), ErrorCode.ORDER_STATE_INVALID);
        assertThat(fake.creates).isZero();
    }
    @Test void timedOutCreationThatExistsCanBeClosedSafely() {
        fake.failCreateAfterStore = true;
        assertError(() -> payments.prepay(id, userId, 1), ErrorCode.PAYMENT_REMOTE_ERROR);
        assertThat(records.current(id).orElseThrow().getPhase()).isEqualTo("UNKNOWN"); assertStock("5.000", "1.500");
        payments.prepareCancellation(id, userId); orders.cancel(user(), id, new CancelOrderRequest("取消"));
        assertStock("5.000", "0.000");
    }
    @Test void missingUnknownTradeDoesNotReleaseReservationsAndCanRetrySameNumber() {
        fake.failCreateBeforeStore = true;
        assertError(() -> payments.prepay(id, userId, 1), ErrorCode.PAYMENT_REMOTE_ERROR);
        String number = records.current(id).orElseThrow().getOutTradeNo();
        assertError(() -> payments.prepareCancellation(id, userId), ErrorCode.PAYMENT_PROCESSING);
        assertStock("5.000", "1.500");
        fake.failCreateBeforeStore = false; payments.prepay(id, userId, 1);
        assertThat(records.current(id).orElseThrow().getOutTradeNo()).isEqualTo(number);
    }
    @Test void alreadyPaidCancellationSettlesBeforeApplyingUserOrMerchantRules() {
        payments.prepay(id, userId, 1); fake.remote = success();
        payments.prepareCancellation(id, userId);
        assertError(() -> orders.cancel(user(), id, new CancelOrderRequest("取消")), ErrorCode.ORDER_STATE_INVALID);
        assertThat(fake.closes).isZero(); assertStock("3.800", "0.000");
        payments.prepareCancellation(id, null);
        admin.cancel(id, new CancelOrderRequest("商家取消"), new AdminPrincipal(1L, "admin", 0L), "test");
        assertThat(orders.detail(user(), id).refundStatus()).isEqualTo("REQUIRED"); assertStock("3.800", "0.000");
    }
    @Test void payingOrNetworkErrorsDoNotReleaseStock() {
        payments.prepay(id, userId, 1);
        fake.remote = state(fake.remote, "USERPAYING");
        assertError(() -> payments.prepareCancellation(id, userId), ErrorCode.PAYMENT_PROCESSING);
        fake.failQuery = true;
        assertError(() -> payments.prepareCancellation(id, userId), ErrorCode.PAYMENT_REMOTE_ERROR);
        assertStock("5.000", "1.500"); assertThat(orders.detail(user(), id).status()).isEqualTo("PENDING_PAYMENT");
    }
    @ParameterizedTest @ValueSource(strings = {"amount", "appid", "mchid", "payer", "currency", "type", "time"})
    void mismatchesNeverChangeOrderOrStockAndAreAudited(String mismatch) {
        payments.prepay(id, userId, 1); var s = success();
        var invalid = new WeChatPayClient.Snapshot(s.outTradeNo(), s.transactionId(), s.state(),
                mismatch.equals("appid") ? "wrong" : s.appid(), mismatch.equals("mchid") ? "wrong" : s.mchid(),
                mismatch.equals("payer") ? "other" : s.openid(), mismatch.equals("amount") ? 1L : s.total(),
                mismatch.equals("currency") ? "USD" : s.currency(), mismatch.equals("type") ? "NATIVE" : "JSAPI",
                mismatch.equals("time") ? s.successTime().plusHours(2) : s.successTime());
        assertThatThrownBy(() -> notifySuccess(invalid)).isInstanceOf(BusinessException.class);
        assertStock("5.000", "1.500"); assertThat(records.current(id).orElseThrow().getState()).isEqualTo("INIT");
        assertThat(db.queryForObject("select count(*) from payment_event_log where out_trade_no=?", Long.class, s.outTradeNo())).isEqualTo(1);
    }
    @Test void ledgerFailureRollsBackPaidOrderAndPaymentRecord() {
        payments.prepay(id, userId, 1);
        db.update("insert into product_stock_ledger(product_spec_id,order_id,change_type,quantity,physical_before,physical_after,reserved_before,"
                + "reserved_after,reason,operator_type,biz_key) values(?,?,'PAY_DEDUCT',-1.2,5,3.8,1.5,0,'测试','PAYMENT',?)",
                spec, id, "PAY_DEDUCT:" + id + ":" + spec);
        assertError(() -> notifySuccess(success()), ErrorCode.PAYMENT_REMOTE_ERROR);
        assertStock("5.000", "1.500"); assertThat(records.current(id).orElseThrow().getState()).isEqualTo("INIT");
        assertThat(orders.detail(user(), id).status()).isEqualTo("PENDING_PAYMENT");
    }
    @Test void lateCallbackWithTimelySuccessStillSettlesAndDuplicateNeverReverts() {
        payments.prepay(id, userId, 1); var t = success();
        db.update("update sales_order set pay_deadline_at=date_sub(current_timestamp,interval 1 minute) where id=?", id);
        notifySuccess(t); notifySuccess(t); assertStock("3.800", "0.000");
    }
    @Test void syncIsThrottledAndRecoversMissedNotification() {
        payments.prepay(id, userId, 1);
        assertError(() -> payments.sync(id, userId), ErrorCode.PAYMENT_PROCESSING);
        db.update("update payment_record set sync_after=date_sub(current_timestamp,interval 5 second) where order_id=?", id);
        fake.remote = success(); payments.sync(id, userId); assertStock("3.800", "0.000");
    }
    @Test void staleOperationTokenIsRecoverableButDoesNotPermitImmediateConcurrentRequest() {
        payments.prepay(id, userId, 1);
        db.update("update payment_record set phase='CREATING',operation_token='stale',operation_until=date_add(current_timestamp,interval 1 minute) where order_id=?", id);
        assertError(() -> payments.prepay(id, userId, 1), ErrorCode.PAYMENT_PROCESSING);
        db.update("update payment_record set operation_until=date_sub(current_timestamp,interval 1 minute) where order_id=?", id);
        payments.prepay(id, userId, 1); assertThat(fake.creates).isEqualTo(1); assertStock("5.000", "1.500");
    }
    @Test void endpointsRequireOwnerAndCallbackDoesNotRequireUserJwt() throws Exception {
        String token = tokens.issue(user()).token();
        for (String action : List.of("prepay", "sync-payment")) {
            mvc.perform(post("/api/v1/orders/" + id + "/" + action).contentType("application/json").content("{\"quoteVersion\":1}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/orders/" + id + "/" + action).header("Authorization", "Bearer " + tokens.issue(new UserPrincipal(userId + 10000)).token())
                            .contentType("application/json").content("{\"quoteVersion\":1}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/v1/orders/" + id + "/prepay").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"quoteVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentParams.signType").value("RSA"));
        fake.notification = new WeChatPayClient.Notification("event-api", "TRANSACTION.SUCCESS", success());
        mvc.perform(post("/api/v1/payments/wechat/notify").contentType("application/json").content("{\"resource\":{}}"))
                .andExpect(status().isNoContent()); assertStock("3.800", "0.000");
    }

    @Test void callbackRacingCancellationNeverReleasesPaidStock() throws Exception {
        payments.prepay(id, userId, 1); var paid = success(); fake.remote = paid;
        race(() -> notifySuccess(paid), () -> {
            payments.prepareCancellation(id, userId);
            assertError(() -> orders.cancel(user(), id, new CancelOrderRequest("取消")), ErrorCode.ORDER_STATE_INVALID);
        });
        assertThat(orders.detail(user(), id).status()).isEqualTo("PAID_WAIT_DELIVERY"); assertStock("3.800", "0.000");
        assertThat(count("product_stock_ledger", "change_type='PAY_DEDUCT'")).isEqualTo(1);
        assertThat(count("product_stock_ledger", "change_type='RELEASE'")).isZero();
    }
    @Test void paymentNotificationCannotReviveClosedCancelledOrder() {
        payments.prepay(id, userId, 1); var paid = success();
        payments.prepareCancellation(id, userId); orders.cancel(user(), id, new CancelOrderRequest("取消"));
        assertError(() -> notifySuccess(paid), ErrorCode.ORDER_STATE_INVALID);
        assertStock("5.000", "0.000"); assertThat(orders.detail(user(), id).status()).isEqualTo("CANCELLED");
    }
    @Test void transactionIdCannotBeAppliedToTwoOrders() {
        payments.prepay(id, userId, 1); var paid = success();
        String number = UUID.randomUUID().toString();
        db.update("insert into sales_order(order_no,wx_user_id,status,address_snapshot,estimated_amount_cent) "
                + "select ?,wx_user_id,'PAID_WAIT_DELIVERY',address_snapshot,estimated_amount_cent from sales_order where id=?", number, id);
        Long other = db.queryForObject("select id from sales_order where order_no=?", Long.class, number);
        db.update("insert into payment_record(order_id,out_trade_no,state,amount_cent,transaction_id,phase) values(?,?,'SUCCESS',3600,?,'SUCCESS')",
                other, number, paid.transactionId());
        assertError(() -> notifySuccess(paid), ErrorCode.PAYMENT_REMOTE_ERROR);
        assertStock("5.000", "1.500"); assertThat(orders.detail(user(), id).status()).isEqualTo("PENDING_PAYMENT");
    }
    @Test void closeFailureKeepsStockUntilConfirmedCloseOnRetry() {
        payments.prepay(id, userId, 1); fake.failClose = true;
        assertError(() -> payments.prepareCancellation(id, userId), ErrorCode.PAYMENT_REMOTE_ERROR);
        assertStock("5.000", "1.500");
        fake.failClose = false; payments.prepareCancellation(id, userId);
        orders.cancel(user(), id, new CancelOrderRequest("取消")); assertStock("5.000", "0.000");
    }
    @Test void concurrentPrepayAndCancelDoNotIssueDuplicateNetworkCalls() throws Exception {
        db.update("update sales_order_item set actual_quantity=1.8 where order_id=?", id);
        fake.entered = new CountDownLatch(1); fake.release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var first = pool.submit(() -> payments.prepay(id, userId, 1));
            assertThat(fake.entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertError(() -> payments.prepay(id, userId, 1), ErrorCode.PAYMENT_PROCESSING);
            assertError(() -> payments.prepareCancellation(id, userId), ErrorCode.PAYMENT_PROCESSING);
            fake.release.countDown(); first.get(10, TimeUnit.SECONDS);
            assertThat(fake.creates).isEqualTo(1); assertStock("5.000", "1.800");
        } finally { fake.release.countDown(); pool.shutdownNow(); }
    }
    @Test void manualRefundRestoresActualWeightOnlyOnce() {
        payments.prepay(id, userId, 1); notifySuccess(success());
        var merchant = new AdminPrincipal(1L, "admin", 0L);
        admin.cancel(id, new CancelOrderRequest("已人工退款"), merchant, "test");
        assertStock("3.800", "0.000");
        admin.refundConfirmed(id, new RefundConfirmRequest("test-refund"), merchant, "test");
        admin.refundConfirmed(id, new RefundConfirmRequest("test-refund"), merchant, "test");
        assertStock("5.000", "0.000");
        assertThat(count("product_stock_ledger", "change_type='REFUND_RELEASE'")).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"CREATED","QUOTED","PENDING_PAYMENT"})
    void timedOutUnpaidOrderClosesAndReleasesOnce(String state) {
        db.update("update sales_order set status=? where id=?", state, id);
        timeoutClock.advance(Duration.ofHours(25));
        assertThat(timeout.process(id)).isTrue(); assertThat(timeout.process(id)).isFalse();
        var order = orders.detail(user(), id);
        assertThat(order.status()).isEqualTo("CLOSED"); assertThat(order.closedAt()).isNotNull();
        assertThat(order.cancelledAt()).isNull(); assertThat(order.canCancel()).isFalse(); assertThat(order.canConfirmQuote()).isFalse();
        assertStock("5.000", "0.000");
        assertThat(count("product_stock_ledger", "change_type='RELEASE'")).isEqualTo(1);
        assertThat(count("order_status_log", "to_status='CLOSED' and operator_type='SYSTEM' and source='TIMEOUT_JOB'")).isEqualTo(1);
    }
    @Test void timeoutClosesWechatBeforeReleasingExtraReservation() {
        db.update("update sales_order_item set actual_quantity=1.8 where order_id=?", id);
        payments.prepay(id, userId, 1); timeoutClock.advance(Duration.ofHours(2));
        assertThat(timeout.process(id)).isTrue(); assertThat(fake.closes).isEqualTo(1);
        assertThat(records.current(id).orElseThrow().getPhase()).isEqualTo("CLOSED"); assertStock("5.000", "0.000");
    }
    @Test void timeoutFindsPaidOrderAndSettlesInsteadOfClosing() {
        payments.prepay(id, userId, 1); fake.remote = success(); timeoutClock.advance(Duration.ofHours(2));
        assertThat(timeout.process(id)).isFalse();
        assertThat(orders.detail(user(), id).status()).isEqualTo("PAID_WAIT_DELIVERY"); assertThat(fake.closes).isZero();
        assertThat(count("product_stock_ledger", "change_type='RELEASE'")).isZero(); assertStock("3.800", "0.000");
    }
    @Test void timeoutUnknownPaymentPersistsBackoffAndRecoversAfterRestartStyleRetry() {
        payments.prepay(id, userId, 1); fake.failQuery = true; timeoutClock.advance(Duration.ofHours(2));
        assertThat(timeout.process(id)).isFalse();
        var failed = orderMapper.findById(id).orElseThrow();
        assertThat(failed.getTimeoutAttemptCount()).isEqualTo(1);
        assertThat(failed.getTimeoutNextAttemptAt()).isCloseTo(java.time.LocalDateTime.ofInstant(timeoutClock.instant(), PaymentTransactions.ZONE).plusSeconds(30),
                within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertStock("5.000", "1.500");
        orderProperties.setTimeoutCloseEnabled(true); isolateTimeoutScan(id);
        int queries = fake.queries; timeout.scan(); assertThat(fake.queries).isEqualTo(queries);
        timeoutClock.advance(Duration.ofSeconds(31)); fake.failQuery = false; timeout.scan();
        assertThat(orders.detail(user(), id).status()).isEqualTo("CLOSED"); assertStock("5.000", "0.000");
    }
    @ParameterizedTest @ValueSource(strings={"USERPAYING","NOT_EXISTS","BUSY","UNAVAILABLE"})
    void timeoutDoesNotReleaseUncertainOrBusyPayment(String condition) {
        payments.prepay(id, userId, 1);
        if (condition.equals("NOT_EXISTS")) fake.remote = null;
        if (condition.equals("USERPAYING")) fake.remote = state(fake.remote, "USERPAYING");
        if (condition.equals("UNAVAILABLE")) fake.failQuery = true;
        if (condition.equals("BUSY")) db.update("update payment_record set operation_token='busy',operation_until=date_add(current_timestamp,interval 1 minute) where order_id=?", id);
        timeoutClock.advance(Duration.ofHours(2)); assertThat(timeout.process(id)).isFalse();
        assertStock("5.000", "1.500"); assertThat(orders.detail(user(), id).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(orderMapper.findById(id).orElseThrow().getTimeoutAttemptCount()).isEqualTo(1);
    }
    @Test void renewedQuoteCannotBeClosedByOldCandidateOrFailureMetadata() {
        timeoutClock.advance(Duration.ofHours(2)); var seen = timeoutTx.observe(id).orElseThrow();
        db.update("update sales_order set status='QUOTED',version=version+1,pay_deadline_at=date_add(current_timestamp,interval 3 hour) where id=?", id);
        assertThat(payments.prepareTimeoutClosure(seen)).isFalse(); assertThat(timeoutTx.close(seen)).isFalse();
        assertThat(timeoutTx.retry(seen)).isEmpty(); assertThat(fake.queries).isZero(); assertStock("5.000", "1.500");
    }
    @Test void closeAndUserCancellationRaceReleaseOneReservation() throws Exception {
        timeoutClock.advance(Duration.ofHours(2));
        race(() -> timeout.process(id), () -> {
            try { orders.cancel(user(), id, new CancelOrderRequest("取消")); }
            catch (BusinessException e) { assertThat(e.errorCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID); }
        });
        assertThat(orders.detail(user(), id).status()).isIn("CLOSED", "CANCELLED");
        assertThat(count("product_stock_ledger", "change_type='RELEASE'")).isEqualTo(1); assertStock("5.000", "0.000");
    }
    @Test void twoTimeoutWorkersCloseOnlyOnce() throws Exception {
        timeoutClock.advance(Duration.ofHours(2)); race(() -> timeout.process(id), () -> timeout.process(id));
        assertThat(count("order_status_log", "to_status='CLOSED'")).isEqualTo(1);
        assertThat(count("product_stock_ledger", "change_type='RELEASE'")).isEqualTo(1); assertStock("5.000", "0.000");
    }
    @Test void localFailureAfterRemoteCloseRollsBackAndNextAttemptRecovers() {
        payments.prepay(id, userId, 1); timeoutClock.advance(Duration.ofHours(2));
        db.update("insert into product_stock_ledger(product_spec_id,order_id,change_type,quantity,physical_before,physical_after,reserved_before,"
                + "reserved_after,reason,operator_type,biz_key) values(?,?,'RELEASE',-1.5,5,5,1.5,0,'冲突','SYSTEM',?)", spec,id,"RELEASE:"+id+":"+spec);
        assertThat(timeout.process(id)).isFalse(); assertThat(records.current(id).orElseThrow().getPhase()).isEqualTo("CLOSED");
        assertStock("5.000", "1.500"); assertThat(count("order_status_log", "to_status='CLOSED'")).isZero();
        db.update("delete from product_stock_ledger where order_id=? and change_type='RELEASE'", id);
        assertThat(timeout.process(id)).isTrue(); assertThat(fake.closes).isEqualTo(1); assertStock("5.000", "0.000");
    }
    @Test void failedOldOrderDoesNotStarveNextBatchAndRetryCounterCaps() {
        Long failedId = id;
        db.update("update product_spec set reserved_qty=0 where id=?", spec);
        fixture(); Long healthyId = id;
        timeoutClock.advance(Duration.ofHours(2)); orderProperties.setTimeoutCloseEnabled(true);
        orderProperties.setTimeoutCloseBatchSize(1); orderProperties.setTimeoutCloseMaxBatches(2);
        db.update("update sales_order set timeout_next_attempt_at=date_add(current_timestamp,interval 7 day) where id not in (?,?)", failedId,healthyId);
        timeout.scan();
        assertThat(orderMapper.findById(failedId).orElseThrow().getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(orderMapper.findById(failedId).orElseThrow().getTimeoutAttemptCount()).isEqualTo(1);
        assertThat(orderMapper.findById(healthyId).orElseThrow().getStatus()).isEqualTo("CLOSED");
        db.update("update sales_order set timeout_attempt_count=2147483647 where id=?", failedId);
        timeout.process(failedId);
        assertThat(orderMapper.findById(failedId).orElseThrow().getTimeoutAttemptCount()).isEqualTo(32);
    }
    @Test void unchangedDeadlineControlsTimeoutAndMissingDeadlineIsSkipped() {
        orderProperties.setPaymentTimeoutMinutes(1);
        timeoutClock.advance(Duration.ofMinutes(2)); assertThat(timeout.process(id)).isFalse();
        db.update("update sales_order set pay_deadline_at=null where id=?", id);
        timeoutClock.advance(Duration.ofDays(2)); assertThat(timeout.process(id)).isFalse();
        assertStock("5.000", "1.500"); orderProperties.setPaymentTimeoutMinutes(60);
    }
    @Test void renewedQuoteClearsOldRetryDelayWithoutLosingAttemptHistory() {
        db.update("update sales_order set status='QUOTED',pay_deadline_at=date_sub(current_timestamp,interval 1 minute) where id=?", id);
        db.update("update product_spec set reserved_qty=0 where id=?", spec);
        assertThat(timeout.process(id)).isFalse();
        assertThat(orderMapper.findById(id).orElseThrow().getTimeoutNextAttemptAt()).isNotNull();
        Long line = db.queryForObject("select id from sales_order_item where order_id=?", Long.class, id);
        admin.quote(id, new QuoteRequest(1,List.of(new QuoteItemRequest(line,new BigDecimal("1.200"),3600L)),3600L,"重新称重"),
                new AdminPrincipal(1L,"admin",0L),"test");
        var renewed = orderMapper.findById(id).orElseThrow();
        assertThat(renewed.getTimeoutNextAttemptAt()).isNull(); assertThat(renewed.getTimeoutAttemptCount()).isEqualTo(1);
        assertThat(timeout.process(id)).isFalse();
    }
    @Test void multiSpecFailureRollsBackEarlierReleasesAndState() {
        Long product = db.queryForObject("select product_id from product_spec where id=?", Long.class, spec);
        db.update("insert into product_spec(product_id,name,quantity_kind,unit,price_cent,physical_stock_qty,reserved_qty) "
                + "values(?,'第二规格','WEIGHT','kg',3000,5,0)",product);
        Long secondSpec = db.queryForObject("select id from product_spec where product_id=? and name='第二规格'",Long.class,product);
        db.update("insert into sales_order_item(order_id,product_id,product_spec_id,product_name,spec_name,quantity_kind,unit,unit_price_cent,"
                + "estimated_quantity,actual_quantity,estimated_amount_cent,final_amount_cent) values(?,?,?,'草鱼','第二规格','WEIGHT','kg',3000,1,1,3000,3000)",
                id,product,secondSpec);
        timeoutClock.advance(Duration.ofHours(2)); assertThat(timeout.process(id)).isFalse();
        assertStock("5.000","1.500"); assertThat(count("product_stock_ledger","change_type='RELEASE'")).isZero();
        assertThat(orders.detail(user(),id).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(count("order_status_log","to_status='CLOSED'")).isZero();
    }
    @Test void timeoutRacingPaidNotificationSettlesOneTimeWithoutRelease() throws Exception {
        payments.prepay(id,userId,1); var paid=success(); fake.remote=paid; timeoutClock.advance(Duration.ofHours(2));
        race(() -> timeout.process(id), () -> notifySuccess(paid));
        assertThat(orders.detail(user(),id).status()).isEqualTo("PAID_WAIT_DELIVERY");
        assertThat(count("product_stock_ledger","change_type='RELEASE'")).isZero();
        assertThat(count("product_stock_ledger","change_type='PAY_DEDUCT'")).isEqualTo(1); assertStock("3.800","0.000");
    }
    private void isolateTimeoutScan(Long only) {
        db.update("update sales_order set timeout_next_attempt_at=date_add(current_timestamp,interval 7 day) where id<>?", only);
    }
    static class MutableClock extends Clock {
        volatile Instant current = Instant.now();
        void advance(Duration duration) { current = current.plus(duration); }
        public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
        public Instant instant() { return current; }
    }

    private long count(String table, String condition) {
        return db.queryForObject("select count(*) from " + table + " where order_id=? and " + condition, Long.class, id);
    }
    private void assertStock(String physical, String reserved) {
        assertThat(db.queryForObject("select physical_stock_qty from product_spec where id=?", BigDecimal.class, spec)).isEqualByComparingTo(physical);
        assertThat(db.queryForObject("select reserved_qty from product_spec where id=?", BigDecimal.class, spec)).isEqualByComparingTo(reserved);
    }
    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).errorCode()).isEqualTo(code);
    }
    private void race(Runnable a, Runnable b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> { start.await(); a.run(); return true; });
            var two = pool.submit(() -> { start.await(); b.run(); return true; }); start.countDown();
            one.get(30, TimeUnit.SECONDS); two.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
    static WeChatPayClient.Snapshot state(WeChatPayClient.Snapshot s, String state) {
        return new WeChatPayClient.Snapshot(s.outTradeNo(), s.transactionId(), state, s.appid(), s.mchid(), s.openid(), s.total(), s.currency(), s.tradeType(), s.successTime());
    }
    static class FakePay implements WeChatPayClient {
        volatile Snapshot remote;
        volatile Notification notification;
        boolean enabled, failCreateBeforeStore, failCreateAfterStore, failQuery, failClose;
        CountDownLatch entered, release;
        int creates, closes, queries;
        void reset() { enabled = true; failCreateBeforeStore = false; failCreateAfterStore = false; failQuery = false; failClose = false;
            creates = 0; closes = 0; queries = 0; remote = null; notification = null; entered = null; release = null; }
        public boolean available() { return enabled; }
        public String create(TradeRequest r) {
            creates++;
            if (failCreateBeforeStore) throw new BusinessException(ErrorCode.PAYMENT_REMOTE_ERROR);
            remote = new Snapshot(r.outTradeNo(), null, "NOTPAY", "wx-test", "mch-test", r.openid(), r.amountCent(), "CNY", "JSAPI", null);
            if (entered != null) {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("测试同步超时"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            if (failCreateAfterStore) throw new BusinessException(ErrorCode.PAYMENT_REMOTE_ERROR);
            return "test-prepay-" + r.outTradeNo();
        }
        public Snapshot query(String no) {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            queries++;
            if (failQuery) throw new BusinessException(ErrorCode.PAYMENT_REMOTE_ERROR);
            return remote == null ? new Snapshot(no, null, "NOT_EXISTS", null, null, null, null, null, null, null) : remote;
        }
        public void close(String no) {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (failClose) throw new BusinessException(ErrorCode.PAYMENT_REMOTE_ERROR); closes++; remote = state(remote, "CLOSED");
        }
        public Map<String,String> sign(String id) { return Map.of("package", "prepay_id=" + id, "signType", "RSA", "paySign", "TEST_ONLY"); }
        public Notification verify(String body, Map<String,String> headers) {
            if (notification == null) throw new BusinessException(ErrorCode.PAYMENT_NOTIFY_INVALID);
            return notification;
        }
    }
}
