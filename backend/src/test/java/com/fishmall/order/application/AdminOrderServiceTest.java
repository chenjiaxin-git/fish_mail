package com.fishmall.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.domain.ProductSpec;
import com.fishmall.catalog.domain.ProductStockLedger;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.catalog.repository.ProductStockLedgerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.api.param.CancelOrderRequest;
import com.fishmall.order.api.param.QuoteItemRequest;
import com.fishmall.order.api.param.QuoteRequest;
import com.fishmall.order.api.param.RefundConfirmRequest;
import com.fishmall.order.application.service.AdminOrderService;
import com.fishmall.order.config.OrderProperties;
import com.fishmall.order.domain.SalesOrder;
import com.fishmall.order.domain.SalesOrderItem;
import com.fishmall.order.repository.OrderPriceRevisionMapper;
import com.fishmall.order.repository.OrderStatusLogMapper;
import com.fishmall.order.repository.PaymentRecordMapper;
import com.fishmall.order.repository.SalesOrderItemMapper;
import com.fishmall.order.repository.SalesOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class AdminOrderServiceTest {
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderItemMapper orderItemMapper;
    @Mock private OrderPriceRevisionMapper priceRevisionMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private OrderStatusLogMapper statusLogMapper;
    @Mock private ProductSpecMapper productSpecMapper;
    @Mock private ProductStockLedgerMapper stockLedgerMapper;
    @Mock private AdminOperationLogMapper operationLogMapper;

    private AdminOrderService service;

    @BeforeEach
    void setUp() {
        OrderProperties properties = new OrderProperties();
        service = new AdminOrderService(orderMapper, orderItemMapper, priceRevisionMapper, paymentRecordMapper,
                statusLogMapper, productSpecMapper, stockLedgerMapper, operationLogMapper, properties, new ObjectMapper());
    }

    @Test
    void quoteUpdatesItemsAndWritesRevision() {
        SalesOrder order = order("CREATED", null, 0);
        SalesOrder quotedOrder = order("QUOTED", 3600L, 1);
        SalesOrderItem item = item(201L, new BigDecimal("1.500"));
        when(orderMapper.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderItemMapper.findByOrderId(10L)).thenReturn(List.of(item));
        when(orderMapper.updateQuote(eq(10L), eq(3600L), eq(0), eq(1), any(LocalDateTime.class), eq(0L))).thenReturn(1);
        when(orderItemMapper.updateQuoted(201L, 10L, new BigDecimal("1.200"), 3600L)).thenReturn(1);
        when(priceRevisionMapper.insert(any())).thenReturn(1);
        when(statusLogMapper.insert(any())).thenReturn(1);
        when(orderMapper.findById(10L)).thenReturn(Optional.of(quotedOrder));
        when(paymentRecordMapper.findByOrderId(10L)).thenReturn(List.of());
        when(statusLogMapper.findByOrderId(10L)).thenReturn(List.of());

        var response = service.quote(10L, new QuoteRequest(0, List.of(new QuoteItemRequest(201L,
                new BigDecimal("1.200"), 3600L)), 3600L, "实际称重1.2kg"), principal(), "trace");

        assertThat(response.finalAmountCent()).isEqualTo(3600L);
        verify(priceRevisionMapper).insert(argThat(revision -> revision.getRevisionNo() == 1
                && revision.getPreviousAmountCent() == 9000L && revision.getFinalAmountCent() == 3600L));
        verify(statusLogMapper).insert(argThat(log -> "CREATED".equals(log.getFromStatus()) && "QUOTED".equals(log.getToStatus())));
    }

    @Test
    void quoteRejectsStaleVersion() {
        SalesOrder order = order("CREATED", 0L, 3);
        when(orderMapper.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.quote(10L, new QuoteRequest(0, List.of(new QuoteItemRequest(201L,
                new BigDecimal("1.200"), 3600L)), 3600L, "改价"), principal(), "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.QUOTE_VERSION_CONFLICT);
    }

    @Test
    void quoteRejectsAmountMismatch() {
        SalesOrder order = order("CREATED", 0L, 0);
        when(orderMapper.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderItemMapper.findByOrderId(10L)).thenReturn(List.of(item(201L, new BigDecimal("1.500"))));

        assertThatThrownBy(() -> service.quote(10L, new QuoteRequest(0, List.of(new QuoteItemRequest(201L,
                new BigDecimal("1.200"), 3600L)), 3500L, "改价"), principal(), "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.ORDER_QUOTE_AMOUNT_MISMATCH);
    }

    @Test
    void unpaidCancelReleasesReservedQuantity() {
        SalesOrder order = order("CREATED", 0L, 0);
        SalesOrderItem item = item(201L, new BigDecimal("2.000"));
        ProductSpec spec = spec(new BigDecimal("10.000"), new BigDecimal("2.000"));
        when(orderMapper.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderMapper.updateCancelled(10L, "用户取消", "NONE", 0L)).thenReturn(1);
        when(orderItemMapper.findByOrderId(10L)).thenReturn(List.of(item));
        when(productSpecMapper.findForUpdateById(30L)).thenReturn(Optional.of(spec));
        when(productSpecMapper.releaseReserved(30L, new BigDecimal("2.000"))).thenReturn(1);
        when(stockLedgerMapper.insert(any())).thenReturn(1);
        when(orderMapper.findById(10L)).thenReturn(Optional.of(order));
        when(priceRevisionMapper.findByOrderId(10L)).thenReturn(List.of());
        when(paymentRecordMapper.findByOrderId(10L)).thenReturn(List.of());
        when(statusLogMapper.findByOrderId(10L)).thenReturn(List.of());

        service.cancel(10L, new CancelOrderRequest("用户取消"), principal(), "trace");

        verify(productSpecMapper).releaseReserved(30L, new BigDecimal("2.000"));
        verify(stockLedgerMapper).insert(argThat(ledger -> "RELEASE".equals(ledger.getChangeType())
                && ledger.getQuantity().compareTo(new BigDecimal("-2.000")) == 0));
    }

    @Test
    void paidCancelRequiresManualRefundAndKeepsStock() {
        SalesOrder order = order("PAID_WAIT_DELIVERY", 5000L, 1);
        when(orderMapper.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderMapper.updateCancelled(10L, "无法配送", "REQUIRED", 0L)).thenReturn(1);
        when(orderMapper.findById(10L)).thenReturn(Optional.of(order));
        stubEmptyDetail(10L);

        service.cancel(10L, new CancelOrderRequest("无法配送"), principal(), "trace");

        verify(productSpecMapper, never()).releaseReserved(any(), any());
        verify(productSpecMapper, never()).restorePhysical(any(), any());
    }

    @Test
    void refundConfirmationRestoresPhysicalStock() {
        SalesOrder order = order("CANCELLED", 5000L, 1);
        order.setRefundStatus("REQUIRED");
        SalesOrderItem item = item(201L, new BigDecimal("1.500"));
        item.setActualQuantity(new BigDecimal("1.200"));
        ProductSpec spec = spec(new BigDecimal("8.800"), BigDecimal.ZERO);
        when(orderMapper.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderMapper.updateRefundConfirmed(10L, "wx-refund-1", 0L)).thenReturn(1);
        when(orderItemMapper.findByOrderId(10L)).thenReturn(List.of(item));
        when(productSpecMapper.findForUpdateById(30L)).thenReturn(Optional.of(spec));
        when(productSpecMapper.restorePhysical(30L, new BigDecimal("1.200"))).thenReturn(1);
        when(stockLedgerMapper.insert(any())).thenReturn(1);
        when(orderMapper.findById(10L)).thenReturn(Optional.of(order));
        when(priceRevisionMapper.findByOrderId(10L)).thenReturn(List.of());
        when(paymentRecordMapper.findByOrderId(10L)).thenReturn(List.of());
        when(statusLogMapper.findByOrderId(10L)).thenReturn(List.of());

        service.refundConfirmed(10L, new RefundConfirmRequest("wx-refund-1"), principal(), "trace");

        verify(productSpecMapper).restorePhysical(30L, new BigDecimal("1.200"));
        verify(stockLedgerMapper).insert(argThat(ledger -> "REFUND_RELEASE".equals(ledger.getChangeType())
                && ledger.getQuantity().compareTo(new BigDecimal("1.200")) == 0));
    }

    @Test
    void repeatedCompleteReturnsCurrentOrderWithoutTransition() {
        SalesOrder order = order("COMPLETED", 5000L, 1);
        when(orderMapper.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        stubEmptyDetail(10L);

        service.complete(10L, principal(), "trace");

        verify(orderMapper, never()).updateCompleted(any(), any());
        verify(statusLogMapper, never()).insert(any());
    }

    private void stubEmptyDetail(Long orderId) {
        when(orderItemMapper.findByOrderId(orderId)).thenReturn(List.of());
        when(priceRevisionMapper.findByOrderId(orderId)).thenReturn(List.of());
        when(paymentRecordMapper.findByOrderId(orderId)).thenReturn(List.of());
        when(statusLogMapper.findByOrderId(orderId)).thenReturn(List.of());
    }

    private SalesOrder order(String status, Long finalAmount, int quoteVersion) {
        SalesOrder order = new SalesOrder();
        order.setId(10L);
        order.setOrderNo("FM20261022000001");
        order.setWxUserId(1L);
        order.setStatus(status);
        order.setSource("MINI_PROGRAM");
        order.setRemark("新鲜");
        order.setAddressSnapshot("{\"contactName\":\"张三\",\"phone\":\"13800000000\",\"detailAddress\":\"鱼市路1号\"}");
        order.setEstimatedAmountCent(9000L);
        order.setFinalAmountCent(finalAmount);
        order.setCurrency("CNY");
        order.setQuoteVersion(quoteVersion);
        order.setRefundStatus("NONE");
        order.setVersion(0L);
        return order;
    }

    private SalesOrderItem item(Long id, BigDecimal estimatedQuantity) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setOrderId(10L);
        item.setProductId(20L);
        item.setProductSpecId(30L);
        item.setProductName("草鱼");
        item.setSpecName("按斤");
        item.setQuantityKind("WEIGHT");
        item.setUnit("kg");
        item.setUnitPriceCent(3000L);
        item.setEstimatedQuantity(estimatedQuantity);
        item.setEstimatedAmountCent(4500L);
        return item;
    }

    private ProductSpec spec(BigDecimal physical, BigDecimal reserved) {
        ProductSpec spec = new ProductSpec();
        spec.setId(30L);
        spec.setPhysicalStockQty(physical);
        spec.setReservedQty(reserved);
        return spec;
    }

    private AdminPrincipal principal() {
        return new AdminPrincipal(1L, "admin", 0L);
    }
}
