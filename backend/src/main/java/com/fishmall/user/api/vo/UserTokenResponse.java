package com.fishmall.user.api.vo;

public record UserTokenResponse(String accessToken, long expiresIn) {
}
