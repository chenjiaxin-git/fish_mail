package com.fishmall.notification;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class NotificationConfig {
    @Bean("notificationScheduler")
    public ThreadPoolTaskScheduler scheduler() {
        var s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix("notification-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(60);
        return s;
    }
}
