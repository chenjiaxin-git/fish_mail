package com.fishmall.notification;

import java.util.Map;

public interface MessageClient {
    record Result(String state, String code, boolean retryable) {}

    Result send(
            String openid,
            String templateId,
            String page,
            String environment,
            Map<String, Map<String, String>> data);
}
