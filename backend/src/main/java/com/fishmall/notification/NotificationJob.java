package com.fishmall.notification;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "notification.worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationJob {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(NotificationJob.class);
    private final JdbcTemplate db;
    private final NotificationTransactions tx;
    private final MessageClient client;
    private final NotificationProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public NotificationJob(
            JdbcTemplate db,
            NotificationTransactions tx,
            MessageClient client,
            NotificationProperties properties,
            ObjectMapper json,
            Clock clock) {
        this.db = db;
        this.tx = tx;
        this.client = client;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Scheduled(
            scheduler = "notificationScheduler",
            fixedDelayString = "${notification.delay-ms:30000}",
            initialDelayString = "${notification.delay-ms:30000}")
    public void run() {
        try {
            tx.expireClaims();
            if (!properties.isEnabled()) {
                tx.skipDisabledPending();
                return;
            }
            var ids =
                    db.queryForList(
                            "select id from message_log where state='PENDING' and"
                                + " next_attempt_at<=? order by id limit ?",
                            Long.class,
                            clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime(),
                            properties.getBatchSize());
            for (Long id : ids) {
                String token = UUID.randomUUID().toString();
                try {
                    var task = tx.claim(id, token);
                    if (task != null) tx.finish(id, token, send(task));
                } catch (Exception e) {
                    log.warn("订阅任务处理失败，messageId={}，保留持久化状态", id);
                }
            }
        } catch (Exception e) {
            log.warn("订阅任务扫描失败，等待下轮重试");
        }
    }

    private MessageClient.Result send(Map<String, Object> task) {
        try {
            var users =
                    db.queryForList(
                            "select openid from wx_user where id=? and status='ACTIVE'",
                            task.get("wx_user_id"));
            if (users.isEmpty()) return new MessageClient.Result("FAILED", "收件用户不可用", false);
            Object raw = task.get("request_json");
            var node =
                    raw instanceof byte[] bytes
                            ? json.readTree(bytes)
                            : json.readTree(String.valueOf(raw));
            var fields = node.path("fields");
            var data = new LinkedHashMap<String, Map<String, String>>();
            var iterator = fields.fields();
            while (iterator.hasNext()) {
                var field = iterator.next();
                String value = node.path(field.getValue().asText()).asText();
                String name = field.getKey();
                int limit =
                        name.startsWith("thing")
                                ? 20
                                : name.startsWith("phrase")
                                        ? 5
                                        : name.startsWith("character_string") ? 32 : 64;
                if (value.isBlank()) return new MessageClient.Result("FAILED", "模板字段为空", false);
                if (name.startsWith("thing") || name.startsWith("phrase"))
                    value =
                            value.codePoints()
                                    .limit(limit)
                                    .collect(
                                            StringBuilder::new,
                                            StringBuilder::appendCodePoint,
                                            StringBuilder::append)
                                    .toString();
                else if (value.codePointCount(0, value.length()) > limit)
                    return new MessageClient.Result("FAILED", "模板字段超长", false);
                data.put(name, Map.of("value", value));
            }
            if (data.isEmpty()) return new MessageClient.Result("FAILED", "模板字段缺失", false);
            return client.send(
                    String.valueOf(users.get(0).get("openid")),
                    String.valueOf(task.get("template_id")),
                    "pages/order-detail/order-detail?id=" + task.get("order_id"),
                    properties.getMiniprogramState(),
                    data);
        } catch (Exception e) {
            return new MessageClient.Result("FAILED", "消息配置或快照无效", false);
        }
    }
}
