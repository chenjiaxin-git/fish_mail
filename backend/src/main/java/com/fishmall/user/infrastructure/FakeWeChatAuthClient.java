package com.fishmall.user.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class FakeWeChatAuthClient implements WeChatAuthClient {
    @Override
    public String code2Session(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("fishmall-fake:" + code).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("fakeopenid_");
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive fake openid", exception);
        }
    }
}
