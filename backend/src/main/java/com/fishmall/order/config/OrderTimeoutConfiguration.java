package com.fishmall.order.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class OrderTimeoutConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class)
    public Clock orderClock() { return Clock.system(ZoneId.of("Asia/Shanghai")); }
    @Bean(name = "orderTimeoutScheduler")
    public ThreadPoolTaskScheduler orderTimeoutScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("order-timeout-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true); scheduler.setAwaitTerminationSeconds(60);
        return scheduler;
    }
}
