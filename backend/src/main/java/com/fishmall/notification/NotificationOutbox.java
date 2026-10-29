package com.fishmall.notification;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class NotificationOutbox {
    private final JdbcTemplate db;
    private final NotificationProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public NotificationOutbox(
            JdbcTemplate db, NotificationProperties properties, ObjectMapper json, Clock clock) {
        this.db = db;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String event, Long orderId, int version) {
        if (!NotificationProperties.EVENTS.contains(event))
            throw new IllegalArgumentException("不支持的通知事件");
        var template = properties.getTemplates().get(event);
        String state = properties.isEnabled() && template != null ? "PENDING" : "SKIPPED";
        var row =
                db.queryForMap(
                        "select"
                            + " order_no,wx_user_id,coalesce(final_amount_cent,estimated_amount_cent)"
                            + " amount from sales_order where id=?",
                        orderId);
        String summary =
                db.queryForObject(
                        "select coalesce(group_concat(product_name order by id separator"
                            + " '、'),'订单商品') from sales_order_item where order_id=?",
                        String.class,
                        orderId);
        var values = new LinkedHashMap<String, Object>();
        values.put("orderNo", row.get("order_no"));
        values.put("summary", summary);
        values.put(
                "amount",
                java.math.BigDecimal.valueOf(((Number) row.get("amount")).longValue(), 2)
                        .toPlainString());
        values.put(
                "time",
                clock.instant()
                        .atZone(ZoneId.of("Asia/Shanghai"))
                        .toLocalDateTime()
                        .format(
                                java.time.format.DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH:mm:ss")));
        values.put(
                "note",
                switch (event) {
                    case "ORDER_CREATED" -> "等待商家称重报价";
                    case "ORDER_QUOTED" -> "请核对最新报价并付款";
                    default -> "订单已配送完成";
                });
        values.put("fields", template == null ? Map.of() : template.fields());
        db.update(
                "insert into"
                    + " message_log(wx_user_id,order_id,template_type,template_id,request_json,state,event_key,quote_version,next_attempt_at,error_message)"
                    + " values (?,?,?,?,?,?,?,?,current_timestamp(3),?) on duplicate key update"
                    + " event_key=event_key",
                row.get("wx_user_id"),
                orderId,
                event,
                template == null ? null : template.id(),
                encode(values),
                state,
                event + ":" + orderId + ":" + version,
                version,
                "SKIPPED".equals(state) ? "发送功能关闭或模板未配置" : null);
    }

    private String encode(Object v) {
        try {
            return json.writeValueAsString(v);
        } catch (Exception e) {
            throw new IllegalStateException("通知快照序列化失败", e);
        }
    }
}
