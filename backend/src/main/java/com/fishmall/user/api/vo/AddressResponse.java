package com.fishmall.user.api.vo;

public record AddressResponse(
        Long id,
        String contactName,
        String phone,
        String detailAddress,
        Boolean isDefault
) {
}
