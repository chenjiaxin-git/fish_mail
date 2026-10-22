package com.fishmall.admin.api;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AdminInfoResponse admin
) {
}
