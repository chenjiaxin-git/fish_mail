package com.fishmall.order.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.cart.repository.CartItemMapper;
import com.fishmall.catalog.domain.Product;
import com.fishmall.catalog.domain.ProductSpec;
import com.fishmall.catalog.domain.ProductStockLedger;
import com.fishmall.catalog.domain.QuantityRules;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.catalog.repository.ProductStockLedgerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.common.MoneyCalculator;
import com.fishmall.order.api.param.CreateOrderItemRequest;
import com.fishmall.order.api.param.CreateOrderRequest;
import com.fishmall.order.api.vo.OrderCreateResponse;
import com.fishmall.order.domain.OrderStateMachine;
import com.fishmall.order.domain.OrderStatus;
import com.fishmall.order.domain.OrderStatusLog;
import com.fishmall.order.domain.SalesOrder;
import com.fishmall.order.domain.SalesOrderItem;
import com.fishmall.order.repository.OrderStatusLogMapper;
import com.fishmall.order.repository.SalesOrderItemMapper;
import com.fishmall.order.repository.SalesOrderMapper;
import com.fishmall.user.domain.UserAddress;
import com.fishmall.user.repository.UserAddressMapper;
import com.fishmall.user.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderCreateService {
    private static final Logger log = LoggerFactory.getLogger(OrderCreateService.class);
    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ORDER_NO_RETRY_TIMES = 3;

    private final UserAddressMapper addressMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductSpecMapper productSpecMapper;
    private final ProductMapper productMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final ProductStockLedgerMapper stockLedgerMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final ObjectMapper objectMapper;
    private final com.fishmall.notification.NotificationOutbox notifications;

    public OrderCreateService(UserAddressMapper addressMapper,
                              CartItemMapper cartItemMapper,
                              ProductSpecMapper productSpecMapper,
                              ProductMapper productMapper,
                              SalesOrderMapper orderMapper,
                              SalesOrderItemMapper orderItemMapper,
                              ProductStockLedgerMapper stockLedgerMapper,
                              OrderStatusLogMapper statusLogMapper,
                              ObjectMapper objectMapper, com.fishmall.notification.NotificationOutbox notifications) {
        this.addressMapper = addressMapper;
        this.cartItemMapper = cartItemMapper;
        this.productSpecMapper = productSpecMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.stockLedgerMapper = stockLedgerMapper;
        this.statusLogMapper = statusLogMapper;
        this.objectMapper = objectMapper;
        this.notifications = notifications;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderCreateResponse create(UserPrincipal principal, CreateOrderRequest request) {
        String idempotencyKey = request.idempotencyKey().trim();
        Optional<SalesOrder> replay = orderMapper.findByUserIdAndIdempotencyKey(principal.wxUserId(), idempotencyKey);
        if (replay.isPresent()) {
            return toCreateResponse(replay.get());
        }
        UserAddress address = addressMapper.findByIdAndUserId(request.addressId(), principal.wxUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
        Map<Long, CreateOrderItemRequest> quantityBySpecId = new LinkedHashMap<>();
        for (CreateOrderItemRequest item : request.items()) {
            if (item.productSpecId() == null || quantityBySpecId.putIfAbsent(item.productSpecId(), item) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "下单商品项重复");
            }
        }
        List<PendingItem> pendingItems = new ArrayList<>();
        for (CreateOrderItemRequest item : quantityBySpecId.values()) {
            pendingItems.add(loadPendingItem(item));
        }
        long estimatedAmountCent = MoneyCalculator.sumCent(pendingItems.stream().map(PendingItem::amountCent).toList());
        OrderStatus initialStatus = OrderStateMachine.validateCreate();

        String remark = request.remark() == null ? null : request.remark().trim();
        SalesOrder order = new SalesOrder();
        order.setWxUserId(principal.wxUserId());
        order.setStatus(initialStatus.name());
        order.setSource("MINI_PROGRAM");
        order.setRemark(remark == null || remark.isEmpty() ? null : remark);
        order.setAddressSnapshot(toAddressSnapshot(address));
        order.setEstimatedAmountCent(estimatedAmountCent);
        order.setCurrency("CNY");
        order.setQuoteVersion(0);
        order.setRefundStatus("NONE");
        order.setIdempotencyKey(idempotencyKey);
        if (!insertOrder(order, principal, idempotencyKey)) {
            return toCreateResponse(orderMapper.findByUserIdAndIdempotencyKey(principal.wxUserId(), idempotencyKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR)));
        }

        for (PendingItem pending : pendingItems) {
            reserveAndWrite(order, pending);
            insertOrderItem(order, pending);
        }
        insertCreateStatusLog(order, principal);
        notifications.enqueue("ORDER_CREATED", order.getId(), 0);
        if (request.cartItemIds() != null && !request.cartItemIds().isEmpty()) {
            cartItemMapper.deleteByIds(principal.wxUserId(), request.cartItemIds());
        }
        log.info("预订单创建完成, orderNo={}, wxUserId={}, estimatedAmountCent={}",
                order.getOrderNo(), principal.wxUserId(), estimatedAmountCent);
        return toCreateResponse(order);
    }

    private PendingItem loadPendingItem(CreateOrderItemRequest item) {
        ProductSpec spec = productSpecMapper.findActiveById(item.productSpecId())
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE));
        Product product = productMapper.findActiveById(spec.getProductId())
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE));
        QuantityRules.validate(item.quantity(), spec);
        long amountCent = MoneyCalculator.itemAmountCent(spec.getPriceCent(), item.quantity());
        return new PendingItem(product, spec, item.quantity(), amountCent);
    }

    private boolean insertOrder(SalesOrder order, UserPrincipal principal, String idempotencyKey) {
        for (int attempt = 1; attempt <= ORDER_NO_RETRY_TIMES; attempt++) {
            order.setOrderNo(generateOrderNo());
            try {
                return orderMapper.insert(order) == 1;
            } catch (DuplicateKeyException exception) {
                if (orderMapper.findByUserIdAndIdempotencyKey(principal.wxUserId(), idempotencyKey).isPresent()) {
                    return false;
                }
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    private void insertOrderItem(SalesOrder order, PendingItem pending) {
        SalesOrderItem orderItem = new SalesOrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setProductId(pending.product().getId());
        orderItem.setProductSpecId(pending.spec().getId());
        orderItem.setProductName(pending.product().getName());
        orderItem.setSpecName(pending.spec().getName());
        orderItem.setQuantityKind(pending.spec().getQuantityKind());
        orderItem.setUnit(pending.spec().getUnit());
        orderItem.setUnitPriceCent(pending.spec().getPriceCent());
        orderItem.setEstimatedQuantity(pending.quantity());
        orderItem.setEstimatedAmountCent(pending.amountCent());
        orderItem.setProductImageUrl(pending.product().getMainImageUrl());
        orderItemMapper.insert(orderItem);
    }

    private void reserveAndWrite(SalesOrder order, PendingItem pending) {
        ProductSpec spec = productSpecMapper.findForUpdateById(pending.spec().getId())
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE));
        if (productSpecMapper.reserve(spec.getId(), pending.quantity()) != 1) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }
        ProductStockLedger ledger = new ProductStockLedger();
        ledger.setProductSpecId(spec.getId());
        ledger.setOrderId(order.getId());
        ledger.setChangeType("RESERVE");
        ledger.setQuantity(pending.quantity());
        ledger.setPhysicalBefore(spec.getPhysicalStockQty());
        ledger.setPhysicalAfter(spec.getPhysicalStockQty());
        ledger.setReservedBefore(spec.getReservedQty());
        ledger.setReservedAfter(spec.getReservedQty().add(pending.quantity()));
        ledger.setReason("下单预占库存");
        ledger.setOperatorType("USER");
        ledger.setOperatorId(order.getWxUserId());
        ledger.setBizKey("RESERVE:" + order.getOrderNo() + ":" + spec.getId());
        stockLedgerMapper.insert(ledger);
    }

    private void insertCreateStatusLog(SalesOrder order, UserPrincipal principal) {
        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(order.getId());
        statusLog.setFromStatus(null);
        statusLog.setToStatus(OrderStateMachine.validateCreate().name());
        statusLog.setOperatorType("USER");
        statusLog.setOperatorId(principal.wxUserId());
        statusLog.setReason("用户提交预订单");
        statusLog.setSource("MINI_PROGRAM");
        statusLogMapper.insert(statusLog);
    }

    private String toAddressSnapshot(UserAddress address) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "contactName", address.getContactName(),
                    "phone", address.getPhone(),
                    "detailAddress", address.getDetailAddress()
            ));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(ORDER_NO_DATE);
        String sequence = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return "FM" + date + sequence;
    }

    private OrderCreateResponse toCreateResponse(SalesOrder order) {
        return new OrderCreateResponse(order.getId(), order.getOrderNo(), order.getStatus(), order.getEstimatedAmountCent());
    }

    private record PendingItem(Product product, ProductSpec spec, BigDecimal quantity, long amountCent) {
    }
}
