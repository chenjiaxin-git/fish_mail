package com.fishmall.user.api.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 50) String contactName,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 200) String detailAddress,
        Boolean isDefault
) {
}
