package com.fishmall.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class NotificationTransactions {
    private final JdbcTemplate db;
    private final Clock clock;
    private final NotificationProperties properties;

    public NotificationTransactions(
            JdbcTemplate db, Clock clock, NotificationProperties properties) {
        this.db = db;
        this.clock = clock;
        this.properties = properties;
    }

    private LocalDateTime now() {
        return clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    }

    @Transactional
    public Map<String, Object> claim(long id, String token) {
        var now = now();
        var rows =
                db.queryForList(
                        "select *,date_format(next_attempt_at,'%Y-%m-%dT%H:%i:%s.%f')"
                            + " next_local,date_format(created_at,'%Y-%m-%dT%H:%i:%s.%f')"
                            + " created_local from message_log where id=? for update",
                        id);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        if (!"PENDING".equals(row.get("state"))) return null;
        if (LocalDateTime.parse(String.valueOf(row.get("next_local"))).isAfter(now)) return null;
        var orders =
                db.queryForList(
                        "select"
                            + " status,quote_version,date_format(pay_deadline_at,'%Y-%m-%dT%H:%i:%s.%f')"
                            + " deadline_local from sales_order where id=?",
                        row.get("order_id"));
        boolean stale =
                orders.isEmpty()
                        || !LocalDateTime.parse(String.valueOf(row.get("created_local")))
                                .plusMinutes(properties.getMaxAgeMinutes())
                                .isAfter(now);
        if (!stale && "ORDER_QUOTED".equals(row.get("template_type"))) {
            var order = orders.get(0);
            stale =
                    !"QUOTED".equals(order.get("status"))
                            || !Objects.equals(row.get("quote_version"), order.get("quote_version"))
                            || order.get("deadline_local") == null
                            || !LocalDateTime.parse(String.valueOf(order.get("deadline_local")))
                                    .isAfter(now);
        }
        if (stale || !properties.isEnabled()) {
            db.update(
                    "update message_log set state='SKIPPED',error_message='事件已过期或发送已关闭' where id=?",
                    id);
            return null;
        }
        db.update(
                "update message_log set"
                    + " state='SENDING',operation_token=?,operation_until=?,attempt_count=attempt_count+1"
                    + " where id=?",
                token,
                now.plusSeconds(60),
                id);
        return row;
    }

    @Transactional
    public void finish(long id, String token, MessageClient.Result result) {
        var counts =
                db.queryForList(
                        "select attempt_count from message_log where id=? and state='SENDING' and"
                            + " operation_token=? for update",
                        id,
                        token);
        if (counts.isEmpty()) return;
        int attempts = ((Number) counts.get(0).get("attempt_count")).intValue();
        String state =
                result.retryable() && attempts < properties.getMaxAttempts()
                        ? "PENDING"
                        : result.state();
        db.update(
                "update message_log set"
                    + " state=?,response_json=json_object('code',?),error_message=?,next_attempt_at=?,operation_token=null,operation_until=null"
                    + " where id=? and operation_token=? and state='SENDING'",
                state,
                result.code(),
                "SUCCESS".equals(state) ? null : result.code(),
                now().plusSeconds(Math.min(300, 30L << (attempts - 1))),
                id,
                token);
    }

    @Transactional
    public void skipDisabledPending() {
        db.update(
                "update message_log set state='SKIPPED',error_message='发送功能已关闭，不补发积压' where"
                    + " state='PENDING'");
    }

    @Transactional
    public void expireClaims() {
        db.update(
                "update message_log set"
                    + " state='UNKNOWN',error_message='发送进程中断，结果未知',operation_token=null,operation_until=null"
                    + " where state='SENDING' and operation_until<=?",
                now());
    }
}
