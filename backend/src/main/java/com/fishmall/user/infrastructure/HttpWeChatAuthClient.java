package com.fishmall.user.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.config.WechatAuthProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpWeChatAuthClient implements WeChatAuthClient {
    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final WechatAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpWeChatAuthClient(WechatAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String code2Session(String code) {
        String url = CODE2SESSION_URL + "?appid=" + properties.appId() + "&secret=" + properties.appSecret()
                + "&js_code=" + code + "&grant_type=authorization_code";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
            }
            JsonNode node = objectMapper.readTree(response.body());
            int errcode = node.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
            }
            String openid = node.path("openid").asText("");
            if (openid.isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
            }
            return openid;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
    }
}
