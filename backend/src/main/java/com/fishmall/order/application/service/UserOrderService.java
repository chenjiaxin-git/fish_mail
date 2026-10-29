package com.fishmall.order.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.catalog.api.vo.PageResponse;
import com.fishmall.catalog.domain.ProductStockLedger;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.catalog.repository.ProductStockLedgerMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.api.param.CancelOrderRequest;
import com.fishmall.order.api.param.ConfirmQuoteRequest;
import com.fishmall.order.api.vo.AddressSnapshotResponse;
import com.fishmall.order.api.vo.UserOrderResponse.*;
import com.fishmall.order.domain.*;
import com.fishmall.order.repository.*;
import com.fishmall.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserOrderService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final SalesOrderMapper orders;
    private final SalesOrderItemMapper items;
    private final OrderPriceRevisionMapper revisions;
    private final OrderStatusLogMapper logs;
    private final ProductSpecMapper specs;
    private final ProductStockLedgerMapper ledger;
    private final ObjectMapper json;
    private final PaymentRecordMapper payments;

    public UserOrderService(SalesOrderMapper orders, SalesOrderItemMapper items, OrderPriceRevisionMapper revisions,
                            OrderStatusLogMapper logs, ProductSpecMapper specs, ProductStockLedgerMapper ledger,
                            ObjectMapper json, PaymentRecordMapper payments) {
        this.orders = orders; this.items = items; this.revisions = revisions; this.logs = logs;
        this.specs = specs; this.ledger = ledger; this.json = json;
        this.payments = payments;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ListItem> list(UserPrincipal principal, String status, int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        if (page < 1 || pageSize < 1 || pageSize > 100 || offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
        List<String> statuses = parseStatuses(status);
        long total = orders.countUser(principal.wxUserId(), statuses);
        var pageOrders = orders.selectUserPage(principal.wxUserId(), statuses, (int) offset, pageSize);
        var byOrder = pageOrders.isEmpty() ? java.util.Map.<Long, List<SalesOrderItem>>of()
                : items.findByOrderIds(pageOrders.stream().map(SalesOrder::getId).toList()).stream()
                    .collect(Collectors.groupingBy(SalesOrderItem::getOrderId));
        return new PageResponse<>(total, page, pageSize, pageOrders.stream().map(o -> new ListItem(
                o.getId(), o.getOrderNo(), o.getStatus(), o.getEstimatedAmountCent(), o.getFinalAmountCent(),
                o.getQuoteVersion(), time(o.getPayDeadlineAt()), time(o.getCreatedAt()), time(o.getUpdatedAt()),
                byOrder.getOrDefault(o.getId(), List.of()).stream().map(i -> new Summary(i.getProductName(),
                        i.getSpecName(), i.getProductImageUrl(), i.getUnit(), i.getEstimatedQuantity(), i.getActualQuantity())).toList()
        )).toList());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail detail(UserPrincipal principal, Long id) {
        return build(require(principal, id, false));
    }

    @Transactional
    public Detail confirm(UserPrincipal principal, Long id, ConfirmQuoteRequest request) {
        SalesOrder order = require(principal, id, true);
        if (order.status() == OrderStatus.PENDING_PAYMENT && request.quoteVersion().equals(order.getQuoteVersion())) {
            return build(order);
        }
        OrderStateMachine.validate(order.status(), OrderAction.USER_CONFIRM_QUOTE);
        if (!request.quoteVersion().equals(order.getQuoteVersion())) {
            throw new BusinessException(ErrorCode.QUOTE_VERSION_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (!confirmable(order, now)) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "报价已过期或无效，无法确认价格");
        }
        if (orders.confirmUserQuote(id, principal.wxUserId(), request.quoteVersion(), order.getVersion(), now) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }
        log(order, principal, "PENDING_PAYMENT", "用户确认价格");
        return build(require(principal, id, false));
    }

    @Transactional
    public Detail cancel(UserPrincipal principal, Long id, CancelOrderRequest request) {
        SalesOrder order = require(principal, id, true);
        if (order.status() == OrderStatus.CANCELLED) return build(order);
        OrderStateMachine.validate(order.status(), OrderAction.USER_CANCEL);
        if (payments.lock(id).filter(p -> !"CLOSED".equals(p.getPhase())).isPresent()) {
            throw new BusinessException(ErrorCode.PAYMENT_PROCESSING);
        }
        String reason = request.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (orders.cancelUser(id, principal.wxUserId(), order.getVersion(), reason) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }
        for (SalesOrderItem item : items.findByOrderId(id).stream()
                .sorted(Comparator.comparing(SalesOrderItem::getProductSpecId)).toList()) {
            var spec = specs.findForUpdateById(item.getProductSpecId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_SPEC_NOT_FOUND));
            var quantity = item.reservedQuantity();
            if (specs.releaseReserved(spec.getId(), quantity) != 1) throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
            ProductStockLedger entry = new ProductStockLedger();
            entry.setOrderId(id); entry.setProductSpecId(spec.getId()); entry.setChangeType("RELEASE");
            entry.setQuantity(quantity.negate()); entry.setPhysicalBefore(spec.getPhysicalStockQty());
            entry.setPhysicalAfter(spec.getPhysicalStockQty()); entry.setReservedBefore(spec.getReservedQty());
            entry.setReservedAfter(spec.getReservedQty().subtract(quantity));
            // 库存流水摘要列为 200 字，完整原因保存在订单和状态日志中。
            entry.setReason(reason.substring(0, Math.min(reason.length(), 200)));
            entry.setOperatorType("USER"); entry.setOperatorId(principal.wxUserId());
            entry.setBizKey("RELEASE:" + id + ":" + spec.getId());
            ledger.insert(entry);
        }
        log(order, principal, "CANCELLED", reason);
        return build(require(principal, id, false));
    }

    private SalesOrder require(UserPrincipal principal, Long id, boolean lock) {
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单编号不合法");
        return (lock ? orders.findByUserForUpdate(id, principal.wxUserId()) : orders.findByUser(id, principal.wxUserId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private Detail build(SalesOrder o) {
        try {
            var address = json.readValue(o.getAddressSnapshot(), AddressSnapshotResponse.class);
            List<Item> detailItems = items.findByOrderId(o.getId()).stream().map(i -> new Item(i.getId(), i.getProductId(),
                    i.getProductSpecId(), i.getProductName(), i.getSpecName(), i.getProductImageUrl(), i.getQuantityKind(),
                    i.getUnit(), i.getUnitPriceCent(), i.getEstimatedQuantity(), i.getActualQuantity(),
                    i.getEstimatedAmountCent(), i.getFinalAmountCent())).toList();
            List<Revision> history = revisions.findByOrderId(o.getId()).stream().map(this::revision).toList();
            return new Detail(o.getId(), o.getOrderNo(), o.getStatus(), o.getRemark(), o.getCurrency(), o.getQuoteVersion(),
                    o.getEstimatedAmountCent(), o.getFinalAmountCent(), address, time(o.getCreatedAt()), time(o.getUpdatedAt()),
                    time(o.getQuotedAt()), time(o.getPayDeadlineAt()), time(o.getPaidAt()), time(o.getDeliveredAt()),
                    time(o.getCompletedAt()), time(o.getCancelledAt()), time(o.getClosedAt()), o.getCancelReason(),
                    o.getRefundStatus(), detailItems, history, confirmable(o, LocalDateTime.now(ZONE)),
                    List.of(OrderStatus.CREATED, OrderStatus.QUOTED, OrderStatus.PENDING_PAYMENT).contains(o.status()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("订单地址快照无法解析, orderId=" + o.getId(), e);
        }
    }

    private Revision revision(OrderPriceRevision r) {
        try {
            var snapshot = json.readTree(r.getItemsSnapshot());
            if (!snapshot.isArray()) throw new IllegalStateException("改价快照必须为数组, revisionId=" + r.getId());
            var safeItems = new java.util.ArrayList<RevisionItem>();
            for (var node : snapshot) {
                safeItems.add(new RevisionItem(node.path("orderItemId").longValue(),
                        node.path("actualQuantity").decimalValue(), node.path("finalAmountCent").longValue()));
            }
            return new Revision(r.getRevisionNo(), r.getPreviousAmountCent(), r.getFinalAmountCent(), r.getReason(),
                    time(r.getCreatedAt()), safeItems);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("订单改价快照无法解析, revisionId=" + r.getId(), e);
        }
    }

    private void log(SalesOrder o, UserPrincipal user, String target, String reason) {
        OrderStatusLog entry = new OrderStatusLog();
        entry.setOrderId(o.getId()); entry.setFromStatus(o.getStatus()); entry.setToStatus(target);
        entry.setOperatorType("USER"); entry.setOperatorId(user.wxUserId()); entry.setSource("MINI_PROGRAM");
        entry.setReason(reason); logs.insert(entry);
    }

    private boolean confirmable(SalesOrder o, LocalDateTime now) {
        return o.status() == OrderStatus.QUOTED && o.getFinalAmountCent() != null
                && o.getFinalAmountCent() >= 0 && o.getPayDeadlineAt() != null && now.isBefore(o.getPayDeadlineAt());
    }

    private OffsetDateTime time(LocalDateTime value) { return value == null ? null : value.atZone(ZONE).toOffsetDateTime(); }

    private List<String> parseStatuses(String status) {
        if (status == null) return List.of();
        if (status.length() > 200) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单状态不合法");
        try {
            return Arrays.stream(status.split(",", -1)).map(String::trim).map(OrderStatus::valueOf)
                    .map(Enum::name).distinct().toList();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单状态不合法");
        }
    }
}
