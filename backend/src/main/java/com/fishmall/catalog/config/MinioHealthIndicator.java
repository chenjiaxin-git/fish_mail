package com.fishmall.catalog.config;

import org.springframework.boot.actuate.health.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.*;
import java.net.http.*;
import java.time.Duration;

@Component("minio")
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class MinioHealthIndicator implements HealthIndicator {
    private final URI ready;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public MinioHealthIndicator(MinioProperties settings) {
        ready = URI.create(settings.endpoint().replaceAll("/+$", "") + "/minio/health/ready");
    }

    @Override
    public Health health() {
        try {
            var response =
                    http.send(
                            HttpRequest.newBuilder(ready)
                                    .timeout(Duration.ofSeconds(2))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200 ? Health.up().build() : Health.down().build();
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Health.down().build();
        }
    }
}
