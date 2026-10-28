package com.fishmall.user.application;

import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.api.param.AddressRequest;
import com.fishmall.user.domain.UserAddress;
import com.fishmall.user.repository.UserAddressMapper;
import com.fishmall.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {
    @Mock private UserAddressMapper addressMapper;

    private UserAddressService service;
    private final UserPrincipal principal = new UserPrincipal(7L);

    @BeforeEach
    void setUp() {
        service = new UserAddressService(addressMapper);
    }

    @Test
    void createClearsPreviousDefault() {
        when(addressMapper.countByUserId(7L)).thenReturn(1L);
        when(addressMapper.clearDefault(7L)).thenReturn(1);
        when(addressMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, UserAddress.class).setId(11L);
            return 1;
        });

        var response = service.create(principal, new AddressRequest("张三", "13800138000", "渔港路 1 号", true));

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.isDefault()).isTrue();
        verify(addressMapper).clearDefault(7L);
    }

    @Test
    void createRejectsOverLimit() {
        when(addressMapper.countByUserId(7L)).thenReturn(20L);

        assertThatThrownBy(() -> service.create(principal, new AddressRequest("张三", "13800138000", "渔港路 1 号", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        verify(addressMapper, never()).insert(any());
    }

    @Test
    void createRejectsInvalidPhone() {
        assertThatThrownBy(() -> service.create(principal, new AddressRequest("张三", "123", "渔港路 1 号", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void updateRejectsForeignAddress() {
        when(addressMapper.findByIdAndUserId(11L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(principal, 11L, new AddressRequest("李四", "13800138001", "渔港路 2 号", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void updateSwitchesDefault() {
        UserAddress existing = address(11L, false);
        when(addressMapper.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(existing));
        when(addressMapper.clearDefault(7L)).thenReturn(1);
        when(addressMapper.update(any())).thenReturn(1);

        var response = service.update(principal, 11L, new AddressRequest("李四", "13800138001", "渔港路 2 号", true));

        assertThat(response.isDefault()).isTrue();
        verify(addressMapper).clearDefault(7L);
    }

    @Test
    void deleteRejectsMissingAddress() {
        when(addressMapper.findByIdAndUserId(11L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(principal, 11L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
        verify(addressMapper, never()).softDelete(11L, 7L);
    }

    private UserAddress address(Long id, boolean isDefault) {
        UserAddress address = new UserAddress();
        address.setId(id);
        address.setWxUserId(7L);
        address.setContactName("张三");
        address.setPhone("13800138000");
        address.setDetailAddress("渔港路 1 号");
        address.setIsDefault(isDefault);
        return address;
    }
}
