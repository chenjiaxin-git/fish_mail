package com.fishmall.notification;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {
    public static final Set<String> EVENTS =
            Set.of("ORDER_CREATED", "ORDER_QUOTED", "ORDER_COMPLETED");
    private boolean enabled;
    private int batchSize = 20, maxAttempts = 3, timeoutMs = 5000, maxAgeMinutes = 1440;
    private String miniprogramState = "formal";
    private Map<String, Template> templates = new LinkedHashMap<>();

    public record Template(String id, Map<String, String> fields) {}

    @PostConstruct
    public void validate() {
        if (batchSize < 1
                || batchSize > 100
                || maxAttempts < 1
                || maxAttempts > 5
                || timeoutMs < 1000
                || timeoutMs > 10000
                || maxAgeMinutes < 1
                || maxAgeMinutes > 1440) throw new IllegalStateException("订阅消息批次、超时或重试参数不合法");
        if (!Set.of("formal", "trial", "developer").contains(miniprogramState))
            throw new IllegalStateException("小程序消息环境不合法");
        if (enabled && !templates.keySet().equals(EVENTS))
            throw new IllegalStateException("必须配置三类订阅模板");
        if (enabled)
            for (String event : EVENTS) {
                Template t = templates.get(event);
                if (t == null
                        || t.id() == null
                        || (!t.id().matches("[A-Za-z0-9_-]{10,100}") || t.id().contains("REPLACE"))
                        || t.fields() == null
                        || t.fields().isEmpty()) throw new IllegalStateException("订阅模板缺失：" + event);
                t.fields()
                        .forEach(
                                (k, v) -> {
                                    if (!k.matches(
                                                    "(thing|character_string|amount|time|date|phrase|number)[1-9][0-9]*")
                                            || !Set.of(
                                                            "orderNo", "summary", "amount", "time",
                                                            "note")
                                                    .contains(v))
                                        throw new IllegalStateException("订阅字段映射不合法：" + event);
                                });
            }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        enabled = v;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int v) {
        batchSize = v;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int v) {
        maxAttempts = v;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int v) {
        timeoutMs = v;
    }

    public int getMaxAgeMinutes() {
        return maxAgeMinutes;
    }

    public void setMaxAgeMinutes(int v) {
        maxAgeMinutes = v;
    }

    public String getMiniprogramState() {
        return miniprogramState;
    }

    public void setMiniprogramState(String v) {
        miniprogramState = v;
    }

    public Map<String, Template> getTemplates() {
        return templates;
    }

    public void setTemplates(Map<String, Template> v) {
        templates = v;
    }
}
