package com.fishmall.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.catalog.domain.ProductSpec;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.catalog.repository.ProductStockLedgerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.api.param.CancelOrderRequest;
import com.fishmall.order.api.param.ConfirmQuoteRequest;
import com.fishmall.order.application.service.UserOrderService;
import com.fishmall.order.domain.*;
import com.fishmall.order.repository.*;
import com.fishmall.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserOrderServiceTest {
    @Mock SalesOrderMapper orders;
    @Mock SalesOrderItemMapper items;
    @Mock OrderPriceRevisionMapper revisions;
    @Mock OrderStatusLogMapper logs;
    @Mock ProductSpecMapper specs;
    @Mock ProductStockLedgerMapper ledger;
    @Mock PaymentRecordMapper payments;
    UserOrderService service;
    final UserPrincipal user = new UserPrincipal(7L);

    @BeforeEach void setup() {
        service = new UserOrderService(orders, items, revisions, logs, specs, ledger, new ObjectMapper(), payments);
    }

    SalesOrder order(String status) {
        SalesOrder o = new SalesOrder();
        o.setId(10L); o.setWxUserId(7L); o.setOrderNo("FM20261029000001"); o.setStatus(status);
        o.setQuoteVersion(2); o.setVersion(4L); o.setFinalAmountCent(0L); o.setEstimatedAmountCent(3000L);
        o.setPayDeadlineAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(10));
        o.setAddressSnapshot("{\"contactName\":\"张三\",\"phone\":\"13800138000\",\"detailAddress\":\"渔港路\"}");
        return o;
    }

    @Test void detailPreservesZeroAndDoesNotExposeInternalFields() throws Exception {
        SalesOrder o = order("QUOTED");
        o.setRefundReference("private"); o.setIdempotencyKey("private-key");
        when(orders.findByUser(10L, 7L)).thenReturn(Optional.of(o));
        var response = service.detail(user, 10L);
        assertThat(response.finalAmountCent()).isZero();
        assertThat(response.canConfirmQuote()).isTrue();
        assertThat(response.payDeadlineAt().getOffset().toString()).isEqualTo("+08:00");
        String body = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertThat(body).doesNotContain("private", "refundReference", "wxUserId", "payments", "statusLogs", "idempotencyKey");
    }

    @Test void foreignOrderReturnsNotFound() {
        assertError(() -> service.detail(user, 999L), ErrorCode.ORDER_NOT_FOUND);
        verifyNoInteractions(items, revisions);
    }

    @Test void listBindsUserAndGroupedStatusesAndBatchesItems() {
        SalesOrder o = order("QUOTED");
        when(orders.selectUserPage(7L, List.of("QUOTED", "PENDING_PAYMENT"), 20, 20)).thenReturn(List.of(o));
        when(orders.countUser(7L, List.of("QUOTED", "PENDING_PAYMENT"))).thenReturn(21L);
        var page = service.list(user, "QUOTED,PENDING_PAYMENT,QUOTED", 2, 20);
        assertThat(page.total()).isEqualTo(21);
        assertThat(page.items()).hasSize(1);
        verify(items).findByOrderIds(List.of(10L));
    }

    @ParameterizedTest @ValueSource(strings = {"", "FAKE", "QUOTED,", "QUOTED,,CREATED"})
    void invalidStatusRejected(String status) {
        assertError(() -> service.list(user, status, 1, 20), ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(orders);
    }

    @Test void invalidPaginationRejected() {
        assertError(() -> service.list(user, null, 0, 20), ErrorCode.VALIDATION_ERROR);
        assertError(() -> service.list(user, null, 1, 101), ErrorCode.VALIDATION_ERROR);
        assertError(() -> service.list(user, null, Integer.MAX_VALUE, 100), ErrorCode.VALIDATION_ERROR);
    }

    @Test void confirmTransitionsWithoutChangingAmountStockOrDeadline() {
        SalesOrder o = order("QUOTED");
        when(orders.findByUserForUpdate(10L, 7L)).thenReturn(Optional.of(o));
        when(orders.confirmUserQuote(eq(10L), eq(7L), eq(2), eq(4L), any())).thenReturn(1);
        when(orders.findByUser(10L, 7L)).thenReturn(Optional.of(order("PENDING_PAYMENT")));
        assertThat(service.confirm(user, 10L, new ConfirmQuoteRequest(2)).status()).isEqualTo("PENDING_PAYMENT");
        verify(logs).insert(argThat(l -> "USER".equals(l.getOperatorType()) && "QUOTED".equals(l.getFromStatus())
                && "PENDING_PAYMENT".equals(l.getToStatus())));
        verifyNoInteractions(specs, ledger);
        assertThat(o.getFinalAmountCent()).isZero();
        assertThat(o.getQuoteVersion()).isEqualTo(2);
    }

    @Test void confirmedReplayDoesNotWriteEvenAfterDeadline() {
        SalesOrder o = order("PENDING_PAYMENT"); o.setPayDeadlineAt(LocalDateTime.MIN);
        when(orders.findByUserForUpdate(10L, 7L)).thenReturn(Optional.of(o));
        assertThat(service.confirm(user, 10L, new ConfirmQuoteRequest(2)).status()).isEqualTo("PENDING_PAYMENT");
        verify(orders, never()).confirmUserQuote(any(), any(), anyInt(), any(), any());
        verifyNoInteractions(logs, specs, ledger);
    }

    @Test void staleQuoteRejected() {
        when(orders.findByUserForUpdate(10L, 7L)).thenReturn(Optional.of(order("QUOTED")));
        assertError(() -> service.confirm(user, 10L, new ConfirmQuoteRequest(1)), ErrorCode.QUOTE_VERSION_CONFLICT);
        verifyNoInteractions(logs, specs, ledger);
    }

    @Test void expiredOrIncompleteQuoteRejected() {
        SalesOrder o = order("QUOTED");
        when(orders.findByUserForUpdate(10L, 7L)).thenReturn(Optional.of(o));
        o.setPayDeadlineAt(LocalDateTime.MIN);
        assertError(() -> service.confirm(user, 10L, new ConfirmQuoteRequest(2)), ErrorCode.ORDER_STATE_INVALID);
        o.setPayDeadlineAt(null);
        assertError(() -> service.confirm(user, 10L, new ConfirmQuoteRequest(2)), ErrorCode.ORDER_STATE_INVALID);
        o.setPayDeadlineAt(LocalDateTime.MAX); o.setFinalAmountCent(null);
        assertError(() -> service.confirm(user, 10L, new ConfirmQuoteRequest(2)), ErrorCode.ORDER_STATE_INVALID);
        verifyNoInteractions(logs, specs, ledger);
    }

    @ParameterizedTest @ValueSource(strings = {"CREATED", "CANCELLED", "CLOSED", "PAID_WAIT_DELIVERY", "DELIVERING", "COMPLETED"})
    void confirmRejectsOtherStates(String state) {
        when(orders.findByUserForUpdate(10L, 7L)).thenReturn(Optional.of(order(state)));
        assertError(() -> service.confirm(user, 10L, new ConfirmQuoteRequest(2)), ErrorCode.ORDER_STATE_INVALID);
    }

    @ParameterizedTest @ValueSource(strings = {"CREATED", "QUOTED", "PENDING_PAYMENT"})
    void cancellationReleasesEstimatedNotActualQuantity(String state) {
        SalesOrder o = order(state);
        SalesOrderItem item = new SalesOrderItem(); item.setProductSpecId(9L);
        item.setEstimatedQuantity(new BigDecimal("1.500")); item.setActualQuantity(new BigDecimal("1.200"));
        ProductSpec spec = new ProductSpec(); spec.setId(9L); spec.setStatus("INACTIVE"); spec.setDeleted(true);
        spec.setPhysicalStockQty(new BigDecimal("5.000")); spec.setReservedQty(new BigDecimal("2.000"));
        when(orders.findByUserForUpdate(10L, 7L)).thenReturn(Optional.of(o));
        when(orders.cancelUser(10L, 7L, 4L, "取消")).thenReturn(1);
        when(items.findByOrderId(10L)).thenReturn(List.of(item));
        when(specs.findForUpdateById(9L)).thenReturn(Optional.of(spec));
        when(specs.releaseReserved(9L, new BigDecimal("1.500"))).thenReturn(1);
        when(orders.findByUser(10L, 7L)).thenReturn(Optional.of(order("CANCELLED")));
        service.cancel(user, 10L, new CancelOrderRequest(" 取消 "));
        verify(ledger).insert(argThat(l -> "RELEASE:10:9".equals(l.getBizKey())
                && l.getReservedAfter().compareTo(new BigDecimal("0.500")) == 0
                && l.getPhysicalAfter().compareTo(new BigDecimal("5.000")) == 0 && "USER".equals(l.getOperatorType())));
        verify(logs).insert(argThat(l -> "CANCELLED".equals(l.getToStatus())));
    }

    @Test void cancelledReplayDoesNotReleaseAgain() {
        when(orders.findByUserForUpdate(10L, 7L)).thenReturn(Optional.of(order("CANCELLED")));
        service.cancel(user, 10L, new CancelOrderRequest("取消"));
        verifyNoInteractions(specs, ledger, logs);
        verify(orders, never()).cancelUser(any(), any(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"PAID_WAIT_DELIVERY", "DELIVERING", "COMPLETED", "CLOSED"})
    void userCannotUseMerchantCancellationPermission(String state) {
        when(orders.findByUserForUpdate(10L, 7L)).thenReturn(Optional.of(order(state)));
        assertError(() -> service.cancel(user, 10L, new CancelOrderRequest("取消")), ErrorCode.ORDER_STATE_INVALID);
        verifyNoInteractions(specs, ledger, logs);
    }

    @Test void commandsDoNotWriteForeignOrders() {
        assertError(() -> service.confirm(user, 10L, new ConfirmQuoteRequest(1)), ErrorCode.ORDER_NOT_FOUND);
        assertError(() -> service.cancel(user, 10L, new CancelOrderRequest("取消")), ErrorCode.ORDER_NOT_FOUND);
        verifyNoInteractions(specs, ledger, logs);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode()).isEqualTo(code);
    }
}
