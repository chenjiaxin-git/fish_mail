package com.fishmall.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.order.api.param.*;
import com.fishmall.order.application.service.*;
import com.fishmall.user.security.UserPrincipal;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(properties = "notification.delay-ms=3600000")
@Testcontainers
class NotificationIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("fishmall")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("admin.jwt-secret", () -> "01234567890123456789012345678901");
        r.add("user.jwt-secret", () -> "01234567890123456789012345678901");
        r.add("admin.initial-password", () -> "test-password-123");
    }

    @Autowired JdbcTemplate db;
    @Autowired NotificationOutbox outbox;
    @Autowired NotificationTransactions tx;
    @Autowired NotificationJob job;
    @Autowired NotificationProperties properties;
    @Autowired PlatformTransactionManager manager;
    @Autowired AdminOrderService admin;
    @Autowired OrderCreateService create;
    @MockBean MessageClient client;
    long user, category, product, spec, address, order;
    final AdminPrincipal merchant = new AdminPrincipal(1L, "admin", 0L);

    @BeforeEach
    void fixture() {
        db.update("delete from message_log");
        properties.setEnabled(true);
        properties.setMaxAttempts(3);
        var templates = new LinkedHashMap<String, NotificationProperties.Template>();
        for (String event : NotificationProperties.EVENTS)
            templates.put(
                    event,
                    new NotificationProperties.Template(
                            "test-template-" + event,
                            Map.of(
                                    "character_string1",
                                    "orderNo",
                                    "thing2",
                                    "summary",
                                    "amount3",
                                    "amount")));
        properties.setTemplates(templates);
        properties.validate();
        String name = UUID.randomUUID().toString();
        db.update("insert into wx_user(openid) values (?)", name);
        user = id("wx_user", "openid", name);
        db.update("insert into product_category(name) values (?)", name);
        category = id("product_category", "name", name);
        db.update(
                "insert into product(category_id,name,status) values (?,?,'ACTIVE')",
                category,
                name);
        product = id("product", "name", name);
        db.update(
                "insert into"
                    + " product_spec(product_id,name,quantity_kind,unit,price_cent,physical_stock_qty,reserved_qty,min_quantity,step_quantity)"
                    + " values (?,'按重量','WEIGHT','kg',3000,100,1.5,0.5,0.5)",
                product);
        spec =
                db.queryForObject(
                        "select id from product_spec where product_id=?", Long.class, product);
        db.update(
                "insert into user_address(wx_user_id,contact_name,phone,detail_address) values"
                    + " (?,'测试用户','13800138000','隔离测试地址')",
                user);
        address =
                db.queryForObject(
                        "select id from user_address where wx_user_id=?", Long.class, user);
        db.update(
                "insert into"
                    + " sales_order(order_no,wx_user_id,status,address_snapshot,estimated_amount_cent,quote_version)"
                    + " values (?,?,'CREATED','{}',4500,0)",
                name.substring(0, 20),
                user);
        order = id("sales_order", "order_no", name.substring(0, 20));
        db.update(
                "insert into"
                    + " sales_order_item(order_id,product_id,product_spec_id,product_name,spec_name,quantity_kind,unit,unit_price_cent,estimated_quantity,estimated_amount_cent)"
                    + " values (?,?,?,'鲜鱼','按重量','WEIGHT','kg',3000,1.5,4500)",
                order,
                product,
                spec);
    }

    long id(String table, String key, String value) {
        return db.queryForObject(
                "select id from " + table + " where " + key + "=?", Long.class, value);
    }

    void enqueue(String event, int version) {
        new TransactionTemplate(manager)
                .executeWithoutResult(s -> outbox.enqueue(event, order, version));
    }

    long message() {
        return db.queryForObject("select id from message_log order by id desc limit 1", Long.class);
    }

    String state() {
        return db.queryForObject(
                "select state from message_log where id=?", String.class, message());
    }

    @Test
    void durableEventsAreUniqueAndRollBackWithBusinessTransaction() {
        enqueue("ORDER_CREATED", 0);
        enqueue("ORDER_CREATED", 0);
        assertThat(db.queryForObject("select count(*) from message_log", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .executeWithoutResult(
                                                s -> {
                                                    outbox.enqueue("ORDER_COMPLETED", order, 0);
                                                    throw new IllegalStateException("rollback");
                                                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(db.queryForObject("select count(*) from message_log", Integer.class))
                .isEqualTo(1);
        assertThat(db.queryForObject("select request_json from message_log", String.class))
                .doesNotContain("openid")
                .doesNotContain("access_token");
    }

    @Test
    void quoteVersionsAndCompletionRegisterOneEventPerTransition() {
        long item =
                db.queryForObject(
                        "select id from sales_order_item where order_id=?", Long.class, order);
        admin.quote(
                order,
                new QuoteRequest(
                        0,
                        List.of(new QuoteItemRequest(item, new BigDecimal("1.2"), 3600L)),
                        3600L,
                        "实称报价"),
                merchant,
                "test");
        admin.quote(
                order,
                new QuoteRequest(
                        1,
                        List.of(new QuoteItemRequest(item, new BigDecimal("1.3"), 3900L)),
                        3900L,
                        "重报"),
                merchant,
                "test");
        assertThat(
                        db.queryForObject(
                                "select count(*) from message_log where"
                                    + " template_type='ORDER_QUOTED'",
                                Integer.class))
                .isEqualTo(2);
        db.update("update sales_order set status='DELIVERING' where id=?", order);
        admin.complete(order, merchant, "test");
        admin.complete(order, merchant, "test");
        assertThat(
                        db.queryForObject(
                                "select count(*) from message_log where"
                                    + " template_type='ORDER_COMPLETED'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void firstCreationAndIdempotentReplayOnlyEnqueueOnce() {
        var request =
                new CreateOrderRequest(
                        address,
                        null,
                        List.of(new CreateOrderItemRequest(spec, new BigDecimal("1.5"))),
                        List.of(),
                        "notification-test-create");
        var a = create.create(new UserPrincipal(user), request);
        var b = create.create(new UserPrincipal(user), request);
        assertThat(a.id()).isEqualTo(b.id());
        assertThat(
                        db.queryForObject(
                                "select count(*) from message_log where order_id=?",
                                Integer.class,
                                a.id()))
                .isEqualTo(1);
    }

    @Test
    void twoWorkersCannotClaimSameTask() throws Exception {
        enqueue("ORDER_CREATED", 0);
        long id = message();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> tx.claim(id, "one"));
            var b = pool.submit(() -> tx.claim(id, "two"));
            assertThat(
                            List.of(Optional.ofNullable(a.get()), Optional.ofNullable(b.get()))
                                    .stream()
                                    .filter(Optional::isPresent)
                                    .count())
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void successAndUnknownAreNotSentTwice() {
        enqueue("ORDER_CREATED", 0);
        when(client.send(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new MessageClient.Result("UNKNOWN", "发送结果未知", false));
        job.run();
        job.run();
        assertThat(state()).isEqualTo("UNKNOWN");
        verify(client, times(1)).send(anyString(), anyString(), anyString(), anyString(), anyMap());
        assertThat(
                        db.queryForObject(
                                "select status from sales_order where id=?", String.class, order))
                .isEqualTo("CREATED");
    }

    @Test
    void knownFailuresBackOffAndStopAtLimit() {
        enqueue("ORDER_CREATED", 0);
        when(client.send(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new MessageClient.Result("FAILED", "WECHAT_-1", true));
        for (int i = 0; i < 3; i++) {
            job.run();
            if (i < 2) {
                assertThat(state()).isEqualTo("PENDING");
                job.run();
                db.update(
                        "update message_log set next_attempt_at='2000-01-01' where id=?",
                        message());
            }
        }
        assertThat(state()).isEqualTo("FAILED");
        assertThat(
                        db.queryForObject(
                                "select attempt_count from message_log where id=?",
                                Integer.class,
                                message()))
                .isEqualTo(3);
        verify(client, times(3)).send(anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void expiredClaimBecomesUnknownAndLateResultCannotOverwriteIt() {
        enqueue("ORDER_CREATED", 0);
        long id = message();
        tx.claim(id, "old");
        db.update("update message_log set operation_until='2000-01-01' where id=?", id);
        tx.expireClaims();
        tx.finish(id, "old", new MessageClient.Result("SUCCESS", "0", false));
        assertThat(state()).isEqualTo("UNKNOWN");
        job.run();
        verifyNoInteractions(client);
    }

    @Test
    void obsoleteQuoteAndDisabledPendingAreSkipped() {
        enqueue("ORDER_QUOTED", 0);
        job.run();
        assertThat(state()).isEqualTo("SKIPPED");
        enqueue("ORDER_CREATED", 0);
        properties.setEnabled(false);
        job.run();
        assertThat(state()).isEqualTo("SKIPPED");
        properties.setEnabled(true);
        job.run();
        verifyNoInteractions(client);
    }

    @Test
    void successfulDeliveryStoresOnlySafeResult() {
        enqueue("ORDER_CREATED", 0);
        when(client.send(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new MessageClient.Result("SUCCESS", "0", false));
        job.run();
        job.run();
        assertThat(state()).isEqualTo("SUCCESS");
        verify(client, times(1))
                .send(
                        anyString(),
                        anyString(),
                        eq("pages/order-detail/order-detail?id=" + order),
                        anyString(),
                        anyMap());
    }

    @Test
    void currentQuoteUsesShanghaiDeadlineEvenWhenJvmIsUtc() {
        db.update(
                "update sales_order set"
                    + " status='QUOTED',quote_version=2,final_amount_cent=3600,pay_deadline_at=current_timestamp+interval"
                    + " 60 minute where id=?",
                order);
        enqueue("ORDER_QUOTED", 2);
        when(client.send(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new MessageClient.Result("SUCCESS", "0", false));
        job.run();
        assertThat(state()).isEqualTo("SUCCESS");
        db.update(
                "update sales_order set quote_version=3,pay_deadline_at=current_timestamp-interval"
                    + " 1 second where id=?",
                order);
        enqueue("ORDER_QUOTED", 3);
        job.run();
        assertThat(state()).isEqualTo("SKIPPED");
        verify(client, times(1)).send(anyString(), anyString(), anyString(), anyString(), anyMap());
    }
}
