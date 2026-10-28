package com.fishmall.user.infrastructure;

public interface WeChatAuthClient {
    String code2Session(String code);
}
