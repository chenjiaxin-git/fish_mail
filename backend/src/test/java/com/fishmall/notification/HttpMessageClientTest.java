package com.fishmall.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.user.config.WechatAuthProperties;

import org.junit.jupiter.api.Test;

import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class HttpMessageClientTest {
    final NotificationProperties config = new NotificationProperties();

    HttpMessageClient client(HttpClient http) {
        config.setEnabled(true);
        return new HttpMessageClient(
                config,
                new WechatAuthProperties("test-app", "test-secret"),
                new ObjectMapper(),
                http);
    }

    HttpResponse<String> response(String body) {
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(body);
        return r;
    }

    MessageClient.Result send(HttpMessageClient c) {
        return c.send(
                "test-openid",
                "test-template",
                "pages/order-detail/order-detail?id=1",
                "developer",
                Map.of("thing1", Map.of("value", "测试")));
    }

    @Test
    void tokenIsCachedAcrossMessages() throws Exception {
        var http = mock(HttpClient.class);
        var tokens = new AtomicInteger();
        when(http.send(any(), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(
                        i -> {
                            HttpRequest r = i.getArgument(0);
                            if (r.uri().getPath().contains("stable_token")) {
                                tokens.incrementAndGet();
                                return response(
                                        "{\"access_token\":\"test-token\",\"expires_in\":7200}");
                            }
                            return response("{\"errcode\":0}");
                        });
        var c = client(http);
        assertThat(send(c).state()).isEqualTo("SUCCESS");
        assertThat(send(c).state()).isEqualTo("SUCCESS");
        assertThat(tokens.get()).isEqualTo(1);
    }

    @Test
    void invalidTokenRefreshesOnlyOnce() throws Exception {
        var http = mock(HttpClient.class);
        when(http.send(any(), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(
                        i -> {
                            HttpRequest r = i.getArgument(0);
                            return r.uri().getPath().contains("stable_token")
                                    ? response(
                                            "{\"access_token\":\"test-token\",\"expires_in\":7200}")
                                    : response("{\"errcode\":40001}");
                        });
        assertThat(send(client(http)).state()).isEqualTo("FAILED");
        verify(http, times(4)).send(any(), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void readTimeoutIsUnknownButConnectTimeoutIsRetryable() throws Exception {
        var http = mock(HttpClient.class);
        var tokenResponse = response("{\"access_token\":\"test-token\",\"expires_in\":7200}");
        when(http.send(any(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse)
                .thenThrow(new HttpTimeoutException("test"));
        assertThat(send(client(http)).state()).isEqualTo("UNKNOWN");
        reset(http);
        when(http.send(any(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse)
                .thenThrow(new HttpConnectTimeoutException("test"));
        assertThat(send(client(http)).retryable()).isTrue();
    }

    @Test
    void refusalAndQuotaFailuresAreNotRetried() {
        for (int code : List.of(43101, 47003, 40037))
            assertThat(HttpMessageClient.classify(code).retryable()).isFalse();
        assertThat(HttpMessageClient.classify(-1).retryable()).isTrue();
    }

    @Test
    void demonstrationAndMissingTemplatesCannotBeEnabled() {
        config.setEnabled(true);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalStateException.class);
        config.setBatchSize(101);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalStateException.class);
    }
}
