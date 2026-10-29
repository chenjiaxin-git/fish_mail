package com.fishmall.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.admin.security.AdminJwtTokenService;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.api.param.*;
import com.fishmall.order.application.service.AdminOrderService;
import com.fishmall.order.application.service.UserOrderService;
import com.fishmall.user.security.UserJwtTokenService;
import com.fishmall.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserOrderIntegrationTest {
    static final String SECRET = "01234567890123456789012345678901";
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fishmall").withUsername("test").withPassword("test");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("admin.jwt-secret", () -> SECRET);
        r.add("admin.initial-password", () -> "initial-pass-123");
        r.add("user.jwt-secret", () -> SECRET);
    }
    @Autowired JdbcTemplate db;
    @Autowired UserOrderService service;
    @Autowired AdminOrderService admin;
    @Autowired UserJwtTokenService tokens;
    @Autowired AdminJwtTokenService adminTokens;
    @Autowired MockMvc mvc;
    UserPrincipal user;
    Long id, specId, itemId;
    String token;
    final AdminPrincipal merchant = new AdminPrincipal(1L, "admin", 0L);

    @BeforeEach void fixture() {
        String name = UUID.randomUUID().toString();
        db.update("insert into wx_user(openid) values (?)", name);
        user = new UserPrincipal(db.queryForObject("select id from wx_user where openid=?", Long.class, name));
        token = tokens.issue(user).token();
        db.update("insert into product_category(name) values (?)", name);
        Long category = db.queryForObject("select id from product_category where name=?", Long.class, name);
        db.update("insert into product(category_id,name,status) values (?,?,'ACTIVE')", category, name);
        Long product = db.queryForObject("select id from product where category_id=?", Long.class, category);
        db.update("insert into product_spec(product_id,name,quantity_kind,unit,price_cent,physical_stock_qty,reserved_qty) "
                + "values (?,'按重量','WEIGHT','kg',3000,5,1.5)", product);
        specId = db.queryForObject("select id from product_spec where product_id=?", Long.class, product);
        db.update("insert into sales_order(order_no,wx_user_id,status,address_snapshot,estimated_amount_cent,final_amount_cent,"
                + "quote_version,quoted_at,pay_deadline_at) values (?,?,'QUOTED',?,4500,3600,1,current_timestamp,date_add(current_timestamp, interval 60 minute))",
                name, user.wxUserId(), "{\"contactName\":\"张三\",\"phone\":\"13800138000\",\"detailAddress\":\"旧地址\"}");
        id = db.queryForObject("select id from sales_order where order_no=?", Long.class, name);
        db.update("insert into sales_order_item(order_id,product_id,product_spec_id,product_name,spec_name,quantity_kind,unit,"
                + "unit_price_cent,estimated_quantity,actual_quantity,estimated_amount_cent,final_amount_cent) "
                + "values (?,?,?,'旧商品名','按重量','WEIGHT','kg',3000,1.5,1.2,4500,3600)", id, product, specId);
        itemId = db.queryForObject("select id from sales_order_item where order_id=?", Long.class, id);
    }

    @Test void repeatedConfirmationWritesOneLogAndNoPaymentOrStockChange() throws Exception {
        var deadline = service.detail(user, id).payDeadlineAt();
        var results = race(() -> service.confirm(user, id, new ConfirmQuoteRequest(1)),
                () -> service.confirm(user, id, new ConfirmQuoteRequest(1)));
        assertThat(results).noneMatch(BusinessException.class::isInstance);
        assertThat(service.detail(user, id).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(service.detail(user, id).payDeadlineAt()).isEqualTo(deadline);
        assertThat(count("order_status_log", "to_status='PENDING_PAYMENT'")).isEqualTo(1);
        assertThat(count("payment_record", "1=1")).isZero();
        assertStock("1.500");
    }

    @Test void userAndMerchantCancelReleaseOnlyOnce() throws Exception {
        race(() -> service.cancel(user, id, new CancelOrderRequest("用户取消订单")),
                () -> admin.cancel(id, new CancelOrderRequest("商家取消"), merchant, "test"));
        assertThat(service.detail(user, id).status()).isEqualTo("CANCELLED");
        assertThat(count("order_status_log", "to_status='CANCELLED'")).isEqualTo(1);
        assertThat(count("product_stock_ledger", "change_type='RELEASE'")).isEqualTo(1);
        assertStock("0.000");
    }

    @Test void repeatedUserCancellationReleasesOnlyOnce() throws Exception {
        race(() -> service.cancel(user, id, new CancelOrderRequest("用户取消订单")),
                () -> service.cancel(user, id, new CancelOrderRequest("用户取消订单")));
        assertThat(count("product_stock_ledger", "change_type='RELEASE'")).isEqualTo(1);
        assertStock("0.000");
    }

    @Test void confirmationRacingNewQuoteNeverConfirmsStaleAmount() throws Exception {
        var result = race(() -> service.confirm(user, id, new ConfirmQuoteRequest(1)),
                () -> admin.quote(id, new QuoteRequest(1, List.of(new QuoteItemRequest(itemId, new BigDecimal("1.300"), 3900L)),
                        3900L, "重新称重"), merchant, "test"));
        assertThat(result.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
        var detail = service.detail(user, id);
        if (detail.status().equals("PENDING_PAYMENT")) {
            assertThat(detail.quoteVersion()).isEqualTo(1); assertThat(detail.finalAmountCent()).isEqualTo(3600);
        } else {
            assertThat(detail.status()).isEqualTo("QUOTED"); assertThat(detail.quoteVersion()).isEqualTo(2);
            assertThat(detail.finalAmountCent()).isEqualTo(3900);
            assertThat(detail.priceRevisions().get(0).items().get(0).actualQuantity()).isEqualByComparingTo("1.300");
        }
        assertStock("1.500");
    }

    @Test void confirmationRacingCancelCannotReviveCancelledOrder() throws Exception {
        race(() -> service.confirm(user, id, new ConfirmQuoteRequest(1)),
                () -> service.cancel(user, id, new CancelOrderRequest("用户取消订单")));
        assertThat(service.detail(user, id).status()).isEqualTo("CANCELLED");
        assertThat(count("order_status_log", "to_status='CANCELLED'")).isEqualTo(1);
        assertStock("0.000");
    }

    @Test void duplicateLedgerFailureRollsBackOrderStockAndLog() {
        db.update("insert into product_stock_ledger(product_spec_id,order_id,change_type,quantity,physical_before,physical_after,"
                + "reserved_before,reserved_after,reason,operator_type,biz_key) values (?,?,'RELEASE',-1.5,5,5,1.5,0,'测试','USER',?)",
                specId, id, "RELEASE:" + id + ":" + specId);
        assertThatThrownBy(() -> service.cancel(user, id, new CancelOrderRequest("取消"))).isInstanceOf(DuplicateKeyException.class);
        assertThat(service.detail(user, id).status()).isEqualTo("QUOTED");
        assertThat(count("order_status_log", "1=1")).isZero(); assertStock("1.500");
    }

    @Test void stockFailureRollsBackStateAndNoLedger() {
        db.update("update product_spec set reserved_qty=0 where id=?", specId);
        assertThatThrownBy(() -> service.cancel(user, id, new CancelOrderRequest("取消")))
                .isInstanceOf(BusinessException.class);
        assertThat(service.detail(user, id).status()).isEqualTo("QUOTED");
        assertThat(count("order_status_log", "1=1")).isZero();
        assertThat(count("product_stock_ledger", "1=1")).isZero();
    }

    @Test void snapshotsSurviveDeletedProductAndLongCancellationReason() {
        db.update("update product_spec set status='DISABLED',deleted=1 where id=?", specId);
        db.update("update product set name='新名字',deleted=1 where id=(select product_id from product_spec where id=?)", specId);
        assertThat(service.detail(user, id).items().get(0).productName()).isEqualTo("旧商品名");
        assertThat(service.detail(user, id).addressSnapshot().detailAddress()).isEqualTo("旧地址");
        service.cancel(user, id, new CancelOrderRequest("长".repeat(500)));
        assertThat(service.detail(user, id).cancelReason()).hasSize(500); assertStock("0.000");
    }

    @Test void expiredQuoteCannotBeConfirmedButCanBeCancelled() {
        db.update("update sales_order set pay_deadline_at=current_timestamp(3) where id=?", id);
        assertThat(service.detail(user, id).canConfirmQuote()).isFalse();
        assertThatThrownBy(() -> service.confirm(user, id, new ConfirmQuoteRequest(1))).isInstanceOf(BusinessException.class);
        assertThat(service.detail(user, id).status()).isEqualTo("QUOTED");
        service.cancel(user, id, new CancelOrderRequest("取消")); assertStock("0.000");
    }

    @Test void merchantQuoteDeadlineUsesShanghaiClockEvenWithUtcJvm() {
        admin.quote(id, new QuoteRequest(1, List.of(new QuoteItemRequest(itemId, new BigDecimal("1.200"), 3600L)),
                3600L, "确认称重"), merchant, "test");
        var detail = service.detail(user, id);
        assertThat(detail.canConfirmQuote()).isTrue();
        assertThat(detail.payDeadlineAt().toInstant()).isBetween(java.time.Instant.now().plusSeconds(3540),
                java.time.Instant.now().plusSeconds(3660));
    }

    @Test void apiEnforcesAuthenticationValidationOwnershipAndWhitelist() throws Exception {
        String foreign = tokens.issue(new UserPrincipal(user.wxUserId() + 10000)).token();
        String expired = expiredToken();
        for (String path : List.of("/api/v1/orders", "/api/v1/orders/" + id)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String bad : List.of(token + "x", expired, adminTokens.issue(merchant).token())) {
                mvc.perform(get(path).header("Authorization", "Bearer " + bad)).andExpect(status().isUnauthorized());
            }
        }
        mvc.perform(get("/api/v1/orders/" + id).header("Authorization", "Bearer " + foreign))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        for (String action : List.of("confirm-quote", "cancel")) {
            String body = action.equals("cancel") ? "{\"reason\":\"取消\"}" : "{\"quoteVersion\":1}";
            mvc.perform(post("/api/v1/orders/" + id + "/" + action).contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/orders/" + id + "/" + action).header("Authorization", "Bearer " + foreign)
                            .contentType("application/json").content(body))
                    .andExpect(status().isNotFound());
        }
        for (String badBody : List.of("{}", "{\"quoteVersion\":0}", "{\"quoteVersion\":\"x\"}")) {
            mvc.perform(post("/api/v1/orders/" + id + "/confirm-quote").header("Authorization", "Bearer " + token)
                            .contentType("application/json").content(badBody))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token).param("pageSize", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/abc").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/0").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].productName").value("旧商品名"))
                .andExpect(jsonPath("$.data.wxUserId").doesNotExist()).andExpect(jsonPath("$.data.payments").doesNotExist())
                .andExpect(jsonPath("$.data.refundReference").doesNotExist());
        assertStock("1.500");
    }

    @Test void listSqlUsesOwnerStatusAndStablePagination() throws Exception {
        assertThat(service.list(user, "QUOTED,PENDING_PAYMENT", 1, 20).items()).hasSize(1);
        assertThat(service.list(user, "CREATED", 1, 20).total()).isZero();
        assertThat(service.list(new UserPrincipal(user.wxUserId() + 10000), null, 1, 20).total()).isZero();
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token)
                        .param("status", "QUOTED,PENDING_PAYMENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].items[0].productName").value("旧商品名"));
        db.update("insert into sales_order(order_no,wx_user_id,status,address_snapshot,estimated_amount_cent,created_at) "
                + "select ?,wx_user_id,'CREATED',address_snapshot,estimated_amount_cent,created_at from sales_order where id=?",
                UUID.randomUUID().toString(), id);
        var first = service.list(user, null, 1, 1);
        var second = service.list(user, null, 2, 1);
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.items().get(0).id()).isGreaterThan(id);
        assertThat(second.items().get(0).id()).isEqualTo(id);
        assertThat(service.list(user, null, 3, 1).items()).isEmpty();
    }

    @Test void apiConfirmsAndCancelsWithReplayAndStableErrors() throws Exception {
        String base = "/api/v1/orders/" + id;
        mvc.perform(post(base + "/confirm-quote").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"quoteVersion\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUOTE_VERSION_CONFLICT"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(base + "/confirm-quote").header("Authorization", "Bearer " + token)
                            .contentType("application/json").content("{\"quoteVersion\":1}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.data.canConfirmQuote").value(false)).andExpect(jsonPath("$.data.canCancel").value(true));
        }
        mvc.perform(post(base + "/cancel").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(base + "/cancel").header("Authorization", "Bearer " + token)
                            .contentType("application/json").content("{\"reason\":\"用户取消订单\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.canCancel").value(false));
        }
        assertThat(count("order_status_log", "to_status='PENDING_PAYMENT'")).isEqualTo(1);
        assertThat(count("order_status_log", "to_status='CANCELLED'")).isEqualTo(1);
        assertThat(count("product_stock_ledger", "change_type='RELEASE'")).isEqualTo(1);
        assertStock("0.000");
    }

    private long count(String table, String filter) {
        return db.queryForObject("select count(*) from " + table + " where order_id=? and " + filter, Long.class, id);
    }
    private String expiredToken() throws Exception {
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        String[] parts = token.split("\\.");
        var json = new ObjectMapper();
        var claims = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
        claims.put("exp", 0);
        String signed = parts[0] + "." + encoder.encodeToString(json.writeValueAsBytes(claims));
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return signed + "." + encoder.encodeToString(mac.doFinal(signed.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private void assertStock(String reserved) {
        assertThat(db.queryForObject("select reserved_qty from product_spec where id=?", BigDecimal.class, specId))
                .isEqualByComparingTo(reserved);
        assertThat(db.queryForObject("select physical_stock_qty from product_spec where id=?", BigDecimal.class, specId))
                .isEqualByComparingTo("5.000");
    }
    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Object> a = () -> attempt(start, first), b = () -> attempt(start, second);
            var f1 = pool.submit(a); var f2 = pool.submit(b); start.countDown();
            return List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    private Object attempt(CountDownLatch latch, Callable<?> action) throws Exception {
        latch.await();
        try { return action.call(); } catch (BusinessException e) { return e; }
    }
}
