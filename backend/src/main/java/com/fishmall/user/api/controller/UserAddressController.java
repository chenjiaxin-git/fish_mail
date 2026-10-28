package com.fishmall.user.api.controller;

import com.fishmall.common.ApiResponse;
import com.fishmall.common.TraceIdFilter;
import com.fishmall.user.api.param.AddressRequest;
import com.fishmall.user.api.vo.AddressResponse;
import com.fishmall.user.application.UserAddressService;
import com.fishmall.user.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class UserAddressController {
    private final UserAddressService addressService;

    public UserAddressController(UserAddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResponse<List<AddressResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(addressService.list(principal), traceId(request));
    }

    @PostMapping
    public ApiResponse<AddressResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddressRequest addressRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(addressService.create(principal, addressRequest), traceId(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest addressRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(addressService.update(principal, id, addressRequest), traceId(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        addressService.delete(principal, id);
        return ApiResponse.success(null, traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
