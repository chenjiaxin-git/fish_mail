package com.fishmall.order.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.catalog.api.vo.PageResponse;
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
import com.fishmall.order.api.vo.AddressSnapshotResponse;
import com.fishmall.order.api.vo.OrderDetailResponse;
import com.fishmall.order.api.vo.OrderItemResponse;
import com.fishmall.order.api.vo.OrderListResponse;
import com.fishmall.order.api.vo.OrderStatusLogResponse;
import com.fishmall.order.api.vo.PaymentResponse;
import com.fishmall.order.api.vo.PriceRevisionResponse;
import com.fishmall.order.config.OrderProperties;
import com.fishmall.order.domain.OrderAction;
import com.fishmall.order.domain.OrderPriceRevision;
import com.fishmall.order.domain.OrderStateMachine;
import com.fishmall.order.domain.OrderStatus;
import com.fishmall.order.domain.OrderStatusLog;
import com.fishmall.order.domain.PaymentRecord;
import com.fishmall.order.domain.SalesOrder;
import com.fishmall.order.domain.SalesOrderItem;
import com.fishmall.order.repository.OrderPriceRevisionMapper;
import com.fishmall.order.repository.OrderStatusLogMapper;
import com.fishmall.order.repository.PaymentRecordMapper;
import com.fishmall.order.repository.SalesOrderItemMapper;
import com.fishmall.order.repository.SalesOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminOrderService {
    private static final Set<String> STATUSES = Set.of("CREATED", "QUOTED", "PENDING_PAYMENT", "PAID_WAIT_DELIVERY",
            "DELIVERING", "COMPLETED", "CANCELLED", "CLOSED");

    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final OrderPriceRevisionMapper priceRevisionMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final ProductSpecMapper productSpecMapper;
    private final ProductStockLedgerMapper stockLedgerMapper;
    private final AdminOperationLogMapper operationLogMapper;
    private final OrderProperties orderProperties;
    private final ObjectMapper objectMapper;

    public AdminOrderService(SalesOrderMapper orderMapper,
                             SalesOrderItemMapper orderItemMapper,
                             OrderPriceRevisionMapper priceRevisionMapper,
                             PaymentRecordMapper paymentRecordMapper,
                             OrderStatusLogMapper statusLogMapper,
                             ProductSpecMapper productSpecMapper,
                             ProductStockLedgerMapper stockLedgerMapper,
                             AdminOperationLogMapper operationLogMapper,
                             OrderProperties orderProperties,
                             ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.priceRevisionMapper = priceRevisionMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.statusLogMapper = statusLogMapper;
        this.productSpecMapper = productSpecMapper;
        this.stockLedgerMapper = stockLedgerMapper;
        this.operationLogMapper = operationLogMapper;
        this.orderProperties = orderProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PageResponse<OrderListResponse> list(String status, String orderNo, String phone,
                                                LocalDate startDate, LocalDate endDate,
                                                int page, int pageSize,
                                                AdminPrincipal principal, String traceId) {
        validatePage(page, pageSize);
        String normalizedStatus = normalize(status);
        validateStatus(normalizedStatus);
        validateQueryText(orderNo, 40, "订单号");
        validateQueryText(phone, 32, "手机号");
        validateDateRange(startDate, endDate, false);
        LocalDateTime startCreatedAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endCreatedAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        long total = orderMapper.count(normalizedStatus, normalize(orderNo), normalize(phone), startCreatedAt, endCreatedAt);
        List<OrderListResponse> items = orderMapper.selectPage(normalizedStatus, normalize(orderNo), normalize(phone),
                startCreatedAt, endCreatedAt, (page - 1) * pageSize, pageSize).stream()
                .map(this::toListResponse).toList();
        operationLogMapper.insert(principal.adminId(), principal.username(), "ORDER", "LIST", null,
                "SUCCESS", null, traceId);
        return new PageResponse<>(total, page, pageSize, items);
    }

    @Transactional
    public OrderDetailResponse detail(Long id, AdminPrincipal principal, String traceId) {
        OrderDetailResponse response = buildDetail(requireOrder(id));
        operationLogMapper.insert(principal.adminId(), principal.username(), "ORDER", "DETAIL",
                String.valueOf(id), "SUCCESS", null, traceId);
        return response;
    }

    @Transactional
    public OrderDetailResponse quote(Long id, QuoteRequest request, AdminPrincipal principal, String traceId) {
        SalesOrder order = requireOrderForUpdate(id);
        OrderStateMachine.validate(order.status(), OrderAction.QUOTE);
        if (!request.quoteVersion().equals(order.getQuoteVersion())) {
            throw new BusinessException(ErrorCode.QUOTE_VERSION_CONFLICT);
        }
        List<SalesOrderItem> items = orderItemMapper.findByOrderId(id);
        Map<Long, SalesOrderItem> itemById = new LinkedHashMap<>();
        for (SalesOrderItem item : items) {
            itemById.put(item.getId(), item);
        }
        Map<Long, QuoteItemRequest> requestedById = new LinkedHashMap<>();
        for (QuoteItemRequest item : request.items()) {
            if (item.orderItemId() == null || requestedById.putIfAbsent(item.orderItemId(), item) != null
                    || !itemById.containsKey(item.orderItemId())) {
                throw new BusinessException(ErrorCode.ORDER_QUOTE_ITEMS_INVALID);
            }
            validateQuoteQuantity(itemById.get(item.orderItemId()), item.actualQuantity());
        }
        if (requestedById.size() != itemById.size()) {
            throw new BusinessException(ErrorCode.ORDER_QUOTE_ITEMS_INVALID);
        }
        long amountSum = 0L;
        for (QuoteItemRequest item : requestedById.values()) {
            if (item.finalAmountCent() > Long.MAX_VALUE - amountSum) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "改价金额超出范围");
            }
            amountSum += item.finalAmountCent();
        }
        if (amountSum != request.finalAmountCent()) {
            throw new BusinessException(ErrorCode.ORDER_QUOTE_AMOUNT_MISMATCH);
        }
        int nextQuoteVersion = Math.addExact(order.getQuoteVersion(), 1);
        int updated = orderMapper.updateQuote(id, request.finalAmountCent(), request.quoteVersion(), nextQuoteVersion,
                LocalDateTime.now().plusMinutes(orderProperties.getPaymentTimeoutMinutes()), order.getVersion());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.QUOTE_VERSION_CONFLICT);
        }
        for (QuoteItemRequest item : requestedById.values()) {
            if (orderItemMapper.updateQuoted(item.orderItemId(), id, item.actualQuantity(), item.finalAmountCent()) != 1) {
                throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
            }
        }
        OrderPriceRevision revision = new OrderPriceRevision();
        revision.setOrderId(id);
        revision.setRevisionNo(nextQuoteVersion);
        revision.setPreviousAmountCent(order.getFinalAmountCent() == null ? order.getEstimatedAmountCent() : order.getFinalAmountCent());
        revision.setFinalAmountCent(request.finalAmountCent());
        revision.setReason(request.reason().trim());
        revision.setItemsSnapshot(toJson(request.items()));
        revision.setAdjustedByType("ADMIN");
        revision.setAdjustedById(principal.adminId());
        priceRevisionMapper.insert(revision);
        insertStatusLog(order, "QUOTED", "商家称重改价", "ADMIN", principal.adminId(), "ADMIN");
        operationLogMapper.insert(principal.adminId(), principal.username(), "ORDER", "QUOTE", order.getOrderNo(),
                "SUCCESS", request.reason().trim(), traceId);
        return buildDetail(requireOrder(id));
    }

    @Transactional
    public OrderDetailResponse deliver(Long id, AdminPrincipal principal, String traceId) {
        SalesOrder order = requireOrderForUpdate(id);
        if (order.status() == OrderStatus.DELIVERING || order.status() == OrderStatus.COMPLETED) {
            return buildDetail(order);
        }
        OrderStateMachine.validate(order.status(), OrderAction.DELIVER);
        if (orderMapper.updateDelivering(id, order.getVersion()) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }
        insertStatusLog(order, "DELIVERING", "商家标记配送中", "ADMIN", principal.adminId(), "ADMIN");
        operationLogMapper.insert(principal.adminId(), principal.username(), "ORDER", "DELIVER", order.getOrderNo(),
                "SUCCESS", null, traceId);
        return buildDetail(requireOrder(id));
    }

    @Transactional
    public OrderDetailResponse complete(Long id, AdminPrincipal principal, String traceId) {
        SalesOrder order = requireOrderForUpdate(id);
        if (order.status() == OrderStatus.COMPLETED) {
            return buildDetail(order);
        }
        OrderStateMachine.validate(order.status(), OrderAction.COMPLETE);
        if (orderMapper.updateCompleted(id, order.getVersion()) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }
        insertStatusLog(order, "COMPLETED", "商家标记完成", "ADMIN", principal.adminId(), "ADMIN");
        operationLogMapper.insert(principal.adminId(), principal.username(), "ORDER", "COMPLETE", order.getOrderNo(),
                "SUCCESS", null, traceId);
        return buildDetail(requireOrder(id));
    }

    @Transactional
    public OrderDetailResponse cancel(Long id, CancelOrderRequest request, AdminPrincipal principal, String traceId) {
        SalesOrder order = requireOrderForUpdate(id);
        if (order.status() == OrderStatus.CANCELLED) {
            return buildDetail(order);
        }
        OrderStateMachine.validate(order.status(), OrderAction.CANCEL);
        boolean paid = OrderStateMachine.isPaid(order.status());
        if (orderMapper.updateCancelled(id, request.reason().trim(), paid ? "REQUIRED" : "NONE", order.getVersion()) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }
        if (!paid) {
            releaseReservation(id, principal, request.reason().trim());
        }
        insertStatusLog(order, "CANCELLED", request.reason().trim(), "ADMIN", principal.adminId(), "ADMIN");
        operationLogMapper.insert(principal.adminId(), principal.username(), "ORDER", "CANCEL", order.getOrderNo(),
                "SUCCESS", request.reason().trim(), traceId);
        return buildDetail(requireOrder(id));
    }

    @Transactional
    public OrderDetailResponse refundConfirmed(Long id, RefundConfirmRequest request,
                                               AdminPrincipal principal, String traceId) {
        SalesOrder order = requireOrderForUpdate(id);
        if (order.status() == OrderStatus.CANCELLED && "MANUAL_REFUNDED".equals(order.getRefundStatus())) {
            return buildDetail(order);
        }
        if (order.status() != OrderStatus.CANCELLED || !"REQUIRED".equals(order.getRefundStatus())) {
            throw new BusinessException(ErrorCode.REFUND_NOT_REQUIRED);
        }
        String refundReference = request.refundReference().trim();
        if (refundReference.isEmpty()) {
            throw new BusinessException(ErrorCode.REFUND_REFERENCE_REQUIRED);
        }
        if (orderMapper.updateRefundConfirmed(id, refundReference, order.getVersion()) != 1) {
            throw new BusinessException(ErrorCode.QUOTE_VERSION_CONFLICT);
        }
        restorePhysicalStock(id, principal, refundReference);
        operationLogMapper.insert(principal.adminId(), principal.username(), "ORDER", "REFUND_CONFIRMED", order.getOrderNo(),
                "SUCCESS", refundReference, traceId);
        return buildDetail(requireOrder(id));
    }

    private void releaseReservation(Long orderId, AdminPrincipal principal, String reason) {
        for (SalesOrderItem item : orderItemMapper.findByOrderId(orderId)) {
            ProductSpec spec = productSpecMapper.findForUpdateById(item.getProductSpecId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND));
            BigDecimal quantity = item.getEstimatedQuantity();
            if (productSpecMapper.releaseReserved(spec.getId(), quantity) != 1) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
            }
            writeStockLedger(spec, item, quantity.negate(), "RELEASE", orderId, principal, reason);
        }
    }

    private void restorePhysicalStock(Long orderId, AdminPrincipal principal, String refundReference) {
        for (SalesOrderItem item : orderItemMapper.findByOrderId(orderId)) {
            ProductSpec spec = productSpecMapper.findForUpdateById(item.getProductSpecId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND));
            BigDecimal quantity = item.getActualQuantity() == null ? item.getEstimatedQuantity() : item.getActualQuantity();
            if (productSpecMapper.restorePhysical(spec.getId(), quantity) != 1) {
                throw new BusinessException(ErrorCode.STOCK_VERSION_CONFLICT);
            }
            writeStockLedger(spec, item, quantity, "REFUND_RELEASE", orderId, principal, "人工退款确认：" + refundReference);
        }
    }

    private void writeStockLedger(ProductSpec spec, SalesOrderItem item, BigDecimal quantity,
                                  String changeType, Long orderId, AdminPrincipal principal, String reason) {
        ProductStockLedger ledger = new ProductStockLedger();
        ledger.setProductSpecId(spec.getId());
        ledger.setOrderId(orderId);
        ledger.setChangeType(changeType);
        ledger.setQuantity(quantity);
        ledger.setPhysicalBefore(spec.getPhysicalStockQty());
        ledger.setPhysicalAfter("REFUND_RELEASE".equals(changeType)
                ? spec.getPhysicalStockQty().add(quantity) : spec.getPhysicalStockQty());
        ledger.setReservedBefore(spec.getReservedQty());
        ledger.setReservedAfter("RELEASE".equals(changeType)
                ? spec.getReservedQty().subtract(quantity.abs()) : spec.getReservedQty());
        ledger.setReason(reason);
        ledger.setOperatorType("ADMIN");
        ledger.setOperatorId(principal.adminId());
        ledger.setBizKey(changeType + ":" + orderId + ":" + item.getProductSpecId());
        stockLedgerMapper.insert(ledger);
    }

    private void insertStatusLog(SalesOrder order, String toStatus, String reason, String operatorType,
                                 Long operatorId, String source) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setFromStatus(order.getStatus());
        log.setToStatus(toStatus);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        log.setSource(source);
        statusLogMapper.insert(log);
    }

    private SalesOrder requireOrder(Long id) {
        return orderMapper.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private SalesOrder requireOrderForUpdate(Long id) {
        return orderMapper.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void validateQuoteQuantity(SalesOrderItem item, BigDecimal quantity) {
        if ("PIECE".equals(item.getQuantityKind()) && quantity.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "件数规格的实称数量必须为整数");
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
    }

    private void validateStatus(String status) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单状态不合法");
        }
    }

    private void validateQueryText(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "长度不合法");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate, boolean requireRange) {
        if (requireRange && (startDate == null || endDate == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出日期范围不能为空");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public AddressSnapshotResponse toAddressResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new AddressSnapshotResponse(text(node, "contactName"), text(node, "phone"), text(node, "detailAddress"));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private OrderListResponse toListResponse(SalesOrder order) {
        AddressSnapshotResponse address = toAddressResponse(order.getAddressSnapshot());
        return new OrderListResponse(order.getId(), order.getOrderNo(), order.getWxUserId(), order.getStatus(),
                address.contactName(), address.phone(), order.getRemark(), order.getEstimatedAmountCent(),
                order.getFinalAmountCent(), order.getRefundStatus(), order.getRefundReference(),
                order.getPayDeadlineAt(), order.getCreatedAt(), order.getUpdatedAt());
    }

    public OrderDetailResponse buildDetail(SalesOrder order) {
        List<OrderItemResponse> items = orderItemMapper.findByOrderId(order.getId()).stream().map(this::toItemResponse).toList();
        List<PriceRevisionResponse> revisions = priceRevisionMapper.findByOrderId(order.getId()).stream().map(this::toRevisionResponse).toList();
        List<PaymentResponse> payments = paymentRecordMapper.findByOrderId(order.getId()).stream().map(this::toPaymentResponse).toList();
        List<OrderStatusLogResponse> statusLogs = statusLogMapper.findByOrderId(order.getId()).stream().map(this::toStatusLogResponse).toList();
        return new OrderDetailResponse(order.getId(), order.getOrderNo(), order.getWxUserId(), order.getStatus(),
                order.getSource(), order.getRemark(), toAddressResponse(order.getAddressSnapshot()),
                order.getEstimatedAmountCent(), order.getFinalAmountCent(), order.getCurrency(), order.getQuoteVersion(),
                order.getQuotedAt(), order.getPayDeadlineAt(), order.getPaidAt(), order.getDeliveredAt(), order.getCompletedAt(),
                order.getClosedAt(), order.getCancelledAt(), order.getCancelReason(), order.getRefundStatus(),
                order.getRefundReference(), order.getCreatedAt(), order.getUpdatedAt(), items, revisions, payments, statusLogs);
    }

    private OrderItemResponse toItemResponse(SalesOrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getProductSpecId(), item.getProductName(),
                item.getSpecName(), item.getQuantityKind(), item.getUnit(), item.getUnitPriceCent(), item.getEstimatedQuantity(),
                item.getActualQuantity(), item.getEstimatedAmountCent(), item.getFinalAmountCent(), item.getProductImageUrl(), item.getCreatedAt());
    }

    private PriceRevisionResponse toRevisionResponse(OrderPriceRevision revision) {
        return new PriceRevisionResponse(revision.getId(), revision.getRevisionNo(), revision.getPreviousAmountCent(),
                revision.getFinalAmountCent(), revision.getReason(), revision.getItemsSnapshot(), revision.getAdjustedByType(),
                revision.getAdjustedById(), revision.getCreatedAt());
    }

    private PaymentResponse toPaymentResponse(PaymentRecord payment) {
        return new PaymentResponse(payment.getId(), payment.getOutTradeNo(), payment.getTransactionId(), payment.getState(),
                payment.getAmountCent(), payment.getTradeState(), payment.getPaidAt(), payment.getCreatedAt());
    }

    private OrderStatusLogResponse toStatusLogResponse(OrderStatusLog statusLog) {
        return new OrderStatusLogResponse(statusLog.getId(), statusLog.getFromStatus(), statusLog.getToStatus(),
                statusLog.getOperatorType(), statusLog.getOperatorId(), statusLog.getReason(), statusLog.getSource(), statusLog.getCreatedAt());
    }
}
