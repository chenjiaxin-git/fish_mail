package com.fishmall.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.cart.repository.CartItemMapper;
import com.fishmall.catalog.domain.Product;
import com.fishmall.catalog.domain.ProductSpec;
import com.fishmall.catalog.domain.ProductStockLedger;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.catalog.repository.ProductStockLedgerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.api.param.CreateOrderItemRequest;
import com.fishmall.order.api.param.CreateOrderRequest;
import com.fishmall.order.api.vo.OrderCreateResponse;
import com.fishmall.order.application.service.OrderCreateService;
import com.fishmall.order.domain.SalesOrder;
import com.fishmall.order.domain.SalesOrderItem;
import com.fishmall.order.repository.OrderStatusLogMapper;
import com.fishmall.order.repository.SalesOrderItemMapper;
import com.fishmall.order.repository.SalesOrderMapper;
import com.fishmall.user.domain.UserAddress;
import com.fishmall.user.repository.UserAddressMapper;
import com.fishmall.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreateServiceTest {
    @Mock private UserAddressMapper addressMapper;
    @Mock private CartItemMapper cartItemMapper;
    @Mock private ProductSpecMapper productSpecMapper;
    @Mock private ProductMapper productMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderItemMapper orderItemMapper;
    @Mock private ProductStockLedgerMapper stockLedgerMapper;
    @Mock private OrderStatusLogMapper statusLogMapper;

    private OrderCreateService service;
    private final UserPrincipal principal = new UserPrincipal(7L);

    @BeforeEach
    void setUp() {
        service = new OrderCreateService(addressMapper, cartItemMapper, productSpecMapper, productMapper,
                orderMapper, orderItemMapper, stockLedgerMapper, statusLogMapper, new ObjectMapper(), org.mockito.Mockito.mock(com.fishmall.notification.NotificationOutbox.class));
    }

    @Test
    void createReservesStockAndWritesSnapshot() {
        stubAddress();
        stubPurchasableSpec();
        when(orderMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, SalesOrder.class).setId(99L);
            return 1;
        });
        when(productSpecMapper.findForUpdateById(20L)).thenReturn(Optional.of(spec()));
        when(productSpecMapper.reserve(20L, new BigDecimal("1.500"))).thenReturn(1);
        when(orderItemMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, SalesOrderItem.class).setId(201L);
            return 1;
        });
        when(stockLedgerMapper.insert(any())).thenReturn(1);
        when(statusLogMapper.insert(any())).thenReturn(1);
        when(cartItemMapper.deleteByIds(eq(7L), any())).thenReturn(1);

        OrderCreateResponse response = service.create(principal, new CreateOrderRequest(12L, "去鳞",
                List.of(new CreateOrderItemRequest(20L, new BigDecimal("1.500"))), List.of(5L), "key-1"));

        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.estimatedAmountCent()).isEqualTo(4500L);
        assertThat(response.orderNo()).startsWith("FM");
        verify(orderItemMapper).insert(argThat(item -> item.getEstimatedAmountCent() == 4500L
                && item.getUnitPriceCent() == 3000L
                && "草鱼".equals(item.getProductName())));
        verify(stockLedgerMapper).insert(argThat((ProductStockLedger ledger) -> "RESERVE".equals(ledger.getChangeType())
                && ledger.getReservedBefore().compareTo(new BigDecimal("0.500")) == 0
                && ledger.getReservedAfter().compareTo(new BigDecimal("2.000")) == 0
                && ledger.getBizKey().contains("RESERVE:")));
        verify(statusLogMapper).insert(argThat(log -> null == log.getFromStatus() && "CREATED".equals(log.getToStatus())));
        verify(cartItemMapper).deleteByIds(eq(7L), eq(List.of(5L)));
    }

    @Test
    void createReplaysExistingOrderByIdempotencyKey() {
        SalesOrder existing = new SalesOrder();
        existing.setId(99L);
        existing.setOrderNo("FM20261028000001");
        existing.setStatus("CREATED");
        existing.setEstimatedAmountCent(4500L);
        when(orderMapper.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.of(existing));

        OrderCreateResponse response = service.create(principal, new CreateOrderRequest(12L, null,
                List.of(new CreateOrderItemRequest(20L, new BigDecimal("1.500"))), List.of(), "key-1"));

        assertThat(response.orderNo()).isEqualTo("FM20261028000001");
        verify(orderMapper, never()).insert(any());
        verify(productSpecMapper, never()).reserve(any(), any());
    }

    @Test
    void createReplaysWhenInsertHitsConcurrentDuplicate() {
        stubAddress();
        stubPurchasableSpec();
        when(orderMapper.insert(any())).thenThrow(new DuplicateKeyException("idempotency"));
        SalesOrder existing = new SalesOrder();
        existing.setId(98L);
        existing.setOrderNo("FM20261028000002");
        existing.setStatus("CREATED");
        existing.setEstimatedAmountCent(4500L);
        when(orderMapper.findByUserIdAndIdempotencyKey(7L, "key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        OrderCreateResponse response = service.create(principal, new CreateOrderRequest(12L, null,
                List.of(new CreateOrderItemRequest(20L, new BigDecimal("1.500"))), List.of(), "key-1"));

        assertThat(response.orderNo()).isEqualTo("FM20261028000002");
        verify(productSpecMapper, never()).reserve(any(), any());
    }

    @Test
    void createRejectsForeignAddress() {
        when(orderMapper.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.empty());
        when(addressMapper.findByIdAndUserId(12L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(principal, new CreateOrderRequest(12L, null,
                List.of(new CreateOrderItemRequest(20L, new BigDecimal("1.500"))), List.of(), "key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void createRejectsDuplicateSpecItems() {
        stubAddress();
        when(orderMapper.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(principal, new CreateOrderRequest(12L, null,
                List.of(new CreateOrderItemRequest(20L, new BigDecimal("1.500")),
                        new CreateOrderItemRequest(20L, new BigDecimal("2.000"))), List.of(), "key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void createRejectsStockInsufficient() {
        stubAddress();
        stubPurchasableSpec();
        when(orderMapper.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.empty());
        when(orderMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, SalesOrder.class).setId(99L);
            return 1;
        });
        when(productSpecMapper.findForUpdateById(20L)).thenReturn(Optional.of(spec()));
        when(productSpecMapper.reserve(20L, new BigDecimal("1.500"))).thenReturn(0);

        assertThatThrownBy(() -> service.create(principal, new CreateOrderRequest(12L, null,
                List.of(new CreateOrderItemRequest(20L, new BigDecimal("1.500"))), List.of(), "key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
    }

    @Test
    void createRejectsOffShelfProduct() {
        stubAddress();
        when(orderMapper.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.empty());
        ProductSpec spec = spec();
        Product product = product();
        product.setStatus("OFF_SHELF");
        when(productSpecMapper.findActiveById(20L)).thenReturn(Optional.of(spec));
        when(productMapper.findActiveById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.create(principal, new CreateOrderRequest(12L, null,
                List.of(new CreateOrderItemRequest(20L, new BigDecimal("1.500"))), List.of(), "key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_AVAILABLE);
    }

    private void stubAddress() {
        UserAddress address = new UserAddress();
        address.setId(12L);
        address.setWxUserId(7L);
        address.setContactName("张三");
        address.setPhone("13800138000");
        address.setDetailAddress("渔港路 1 号");
        when(orderMapper.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.empty());
        when(addressMapper.findByIdAndUserId(12L, 7L)).thenReturn(Optional.of(address));
    }

    private void stubPurchasableSpec() {
        when(productSpecMapper.findActiveById(20L)).thenReturn(Optional.of(spec()));
        when(productMapper.findActiveById(10L)).thenReturn(Optional.of(product()));
    }

    private ProductSpec spec() {
        ProductSpec spec = new ProductSpec();
        spec.setId(20L);
        spec.setProductId(10L);
        spec.setName("按斤");
        spec.setQuantityKind("WEIGHT");
        spec.setUnit("kg");
        spec.setPriceCent(3000L);
        spec.setMinQuantity(new BigDecimal("1.000"));
        spec.setStepQuantity(new BigDecimal("0.500"));
        spec.setPhysicalStockQty(new BigDecimal("5.500"));
        spec.setReservedQty(new BigDecimal("0.500"));
        spec.setStatus("ACTIVE");
        return spec;
    }

    private Product product() {
        Product product = new Product();
        product.setId(10L);
        product.setName("草鱼");
        product.setMainImageUrl("/uploads/products/uuid.jpg");
        product.setStatus("ACTIVE");
        return product;
    }
}
