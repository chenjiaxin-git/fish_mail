package com.fishmall.order.application;

import com.fishmall.catalog.domain.Product;
import com.fishmall.catalog.domain.ProductCategory;
import com.fishmall.catalog.domain.ProductSpec;
import com.fishmall.catalog.repository.ProductCategoryMapper;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.api.param.CreateOrderItemRequest;
import com.fishmall.order.api.param.CreateOrderRequest;
import com.fishmall.order.api.vo.OrderCreateResponse;
import com.fishmall.order.application.service.OrderCreateService;
import com.fishmall.user.domain.UserAddress;
import com.fishmall.user.domain.WxUser;
import com.fishmall.user.repository.UserAddressMapper;
import com.fishmall.user.repository.WxUserMapper;
import com.fishmall.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderCreateConcurrencyTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fishmall")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("admin.jwt-secret", () -> "01234567890123456789012345678901");
        registry.add("admin.initial-password", () -> "initial-pass-123");
        registry.add("user.jwt-secret", () -> "01234567890123456789012345678901");
    }

    @Autowired private OrderCreateService orderCreateService;
    @Autowired private WxUserMapper wxUserMapper;
    @Autowired private UserAddressMapper addressMapper;
    @Autowired private ProductCategoryMapper categoryMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private ProductSpecMapper productSpecMapper;

    private UserPrincipal principal;
    private Long addressId;

    @BeforeEach
    void setUp() {
        WxUser user = new WxUser();
        user.setOpenid("openid-" + UUID.randomUUID());
        wxUserMapper.insert(user);
        principal = new UserPrincipal(user.getId());
        UserAddress address = new UserAddress();
        address.setWxUserId(user.getId());
        address.setContactName("张三");
        address.setPhone("13800138000");
        address.setDetailAddress("渔港路 1 号");
        address.setIsDefault(true);
        addressMapper.insert(address);
        addressId = address.getId();
    }

    @Test
    void concurrentOrdersCompeteForLastItem() throws Exception {
        Long specId = createSpecWithStock(new BigDecimal("1.000"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> attemptOrder(start, specId, "key-a-" + UUID.randomUUID()));
            Future<Object> second = pool.submit(() -> attemptOrder(start, specId, "key-b-" + UUID.randomUUID()));
            start.countDown();
            Object result1 = first.get();
            Object result2 = second.get();

            List<Object> results = List.of(result1, result2);
            long successCount = results.stream().filter(OrderCreateResponse.class::isInstance).count();
            long failureCount = results.stream().filter(BusinessException.class::isInstance).count();
            assertThat(successCount).isEqualTo(1);
            assertThat(failureCount).isEqualTo(1);
            BusinessException failure = (BusinessException) results.stream()
                    .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
        } finally {
            pool.shutdownNow();
        }
        ProductSpec spec = productSpecMapper.findActiveById(specId).orElseThrow();
        assertThat(spec.getReservedQty()).isEqualByComparingTo("1.000");
        assertThat(spec.getPhysicalStockQty()).isEqualByComparingTo("1.000");
    }

    @Test
    void concurrentReplayWithSameIdempotencyKeyCreatesOneOrder() throws Exception {
        Long specId = createSpecWithStock(new BigDecimal("2.000"));
        String idempotencyKey = "same-key-" + UUID.randomUUID();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> attemptOrder(start, specId, idempotencyKey));
            Future<Object> second = pool.submit(() -> attemptOrder(start, specId, idempotencyKey));
            start.countDown();
            OrderCreateResponse result1 = (OrderCreateResponse) first.get();
            OrderCreateResponse result2 = (OrderCreateResponse) second.get();

            assertThat(result1.orderNo()).isEqualTo(result2.orderNo());
        } finally {
            pool.shutdownNow();
        }
        ProductSpec spec = productSpecMapper.findActiveById(specId).orElseThrow();
        assertThat(spec.getReservedQty()).isEqualByComparingTo("1.000");
    }

    private Object attemptOrder(CountDownLatch start, Long specId, String idempotencyKey) {
        try {
            start.await();
            return orderCreateService.create(principal, new CreateOrderRequest(addressId, "并发测试",
                    List.of(new CreateOrderItemRequest(specId, new BigDecimal("1.000"))), List.of(), idempotencyKey));
        } catch (BusinessException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return exception;
        }
    }

    private Long createSpecWithStock(BigDecimal stock) {
        ProductCategory category = new ProductCategory();
        category.setName("并发测试分类-" + UUID.randomUUID());
        category.setSort(0);
        category.setStatus("ACTIVE");
        categoryMapper.insert(category);

        Product product = new Product();
        product.setCategoryId(category.getId());
        product.setName("并发测试草鱼");
        product.setStatus("ACTIVE");
        productMapper.insert(product);

        ProductSpec spec = new ProductSpec();
        spec.setProductId(product.getId());
        spec.setName("按斤");
        spec.setQuantityKind("WEIGHT");
        spec.setUnit("kg");
        spec.setPriceCent(3000L);
        spec.setMinQuantity(new BigDecimal("1.000"));
        spec.setStepQuantity(new BigDecimal("0.500"));
        spec.setPhysicalStockQty(stock);
        spec.setReservedQty(new BigDecimal("0.000"));
        spec.setWarningQty(new BigDecimal("0.000"));
        spec.setStatus("ACTIVE");
        productSpecMapper.insert(spec);
        return spec.getId();
    }
}
