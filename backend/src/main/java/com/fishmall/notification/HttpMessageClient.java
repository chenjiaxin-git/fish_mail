package com.fishmall.notification;

import com.fasterxml.jackson.databind.*;
import com.fishmall.user.config.WechatAuthProperties;

import org.springframework.stereotype.Component;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

@Component
public class HttpMessageClient implements MessageClient {
    private final NotificationProperties config;
    private final WechatAuthProperties wechat;
    private final ObjectMapper json;
    private final HttpClient http;
    private String cachedToken;
    private Instant tokenUntil = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    public HttpMessageClient(
            NotificationProperties config, WechatAuthProperties wechat, ObjectMapper json) {
        this(
                config,
                wechat,
                json,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
                        .build());
    }

    HttpMessageClient(
            NotificationProperties config,
            WechatAuthProperties wechat,
            ObjectMapper json,
            HttpClient http) {
        this.config = config;
        this.wechat = wechat;
        this.json = json;
        this.http = http;
        if (config.isEnabled()
                && (wechat.appId() == null
                        || wechat.appId().isBlank()
                        || wechat.appSecret() == null
                        || wechat.appSecret().isBlank()))
            throw new IllegalStateException("订阅消息需要真实微信AppID和Secret");
    }

    private synchronized String token(boolean refresh) throws Exception {
        if (refresh) {
            cachedToken = null;
            tokenUntil = Instant.EPOCH;
        }
        if (cachedToken != null && Instant.now().isBefore(tokenUntil)) return cachedToken;
        var request =
                HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com/cgi-bin/stable_token"))
                        .timeout(Duration.ofMillis(config.getTimeoutMs()))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "grant_type",
                                                        "client_credential",
                                                        "appid",
                                                        wechat.appId(),
                                                        "secret",
                                                        wechat.appSecret(),
                                                        "force_refresh",
                                                        false))))
                        .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        var body = json.readTree(response.body());
        if (response.statusCode() != 200
                || body.path("access_token").asText().isBlank()
                || body.path("expires_in").asInt() < 120)
            throw new IllegalStateException("获取微信Token失败");
        cachedToken = body.path("access_token").asText();
        tokenUntil = Instant.now().plusSeconds(body.path("expires_in").asInt() - 60);
        return cachedToken;
    }

    @Override
    public Result send(
            String openid,
            String templateId,
            String page,
            String environment,
            Map<String, Map<String, String>> data) {
        if (!config.isEnabled()) return new Result("SKIPPED", "发送功能关闭", false);
        for (int attempt = 0; attempt < 2; attempt++) {
            String accessToken;
            try {
                accessToken = token(attempt > 0);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return new Result("FAILED", "微信Token暂不可用", true);
            }
            String body;
            try {
                body =
                        json.writeValueAsString(
                                Map.of(
                                        "touser",
                                        openid,
                                        "template_id",
                                        templateId,
                                        "page",
                                        page,
                                        "miniprogram_state",
                                        environment,
                                        "lang",
                                        "zh_CN",
                                        "data",
                                        data));
            } catch (Exception e) {
                return new Result("FAILED", "模板内容无效", false);
            }
            try {
                var request =
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token="
                                                        + URLEncoder.encode(
                                                                accessToken,
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)))
                                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return new Result("UNKNOWN", "微信响应状态异常", false);
                JsonNode result = json.readTree(response.body());
                if (!result.has("errcode") || !result.path("errcode").canConvertToInt())
                    return new Result("UNKNOWN", "微信响应格式异常", false);
                int code = result.path("errcode").asInt();
                if ((code == 40001 || code == 42001) && attempt == 0) continue;
                return classify(code);
            } catch (HttpConnectTimeoutException e) {
                return new Result("FAILED", "微信连接超时", true);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return new Result("UNKNOWN", "发送结果未知", false);
            }
        }
        return new Result("FAILED", "微信Token持续失效", false);
    }

    public static Result classify(int code) {
        if (code == 0) return new Result("SUCCESS", "0", false);
        return new Result("FAILED", "WECHAT_" + code, code == -1 || code == 45009);
    }
}
