package com.fishmall.notification;

import com.fishmall.common.*;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class SubscriptionConfigController {
    private final NotificationProperties properties;

    public SubscriptionConfigController(NotificationProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/v1/users/subscription-config")
    public ApiResponse<Map<String, Object>> config(HttpServletRequest request) {
        var templates = new LinkedHashMap<String, String>();
        if (properties.isEnabled())
            properties.getTemplates().forEach((k, v) -> templates.put(k, v.id()));
        return ApiResponse.success(
                Map.of("enabled", properties.isEnabled(), "templates", templates),
                String.valueOf(request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)));
    }
}
