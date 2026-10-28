package com.fishmall.user.application;

import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.api.param.AddressRequest;
import com.fishmall.user.api.vo.AddressResponse;
import com.fishmall.user.domain.UserAddress;
import com.fishmall.user.repository.UserAddressMapper;
import com.fishmall.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class UserAddressService {
    private static final int MAX_ADDRESS_COUNT = 20;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final UserAddressMapper addressMapper;

    public UserAddressService(UserAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UserPrincipal principal) {
        return addressMapper.findByUserId(principal.wxUserId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AddressResponse create(UserPrincipal principal, AddressRequest request) {
        UserAddress address = build(principal.wxUserId(), request);
        if (addressMapper.countByUserId(principal.wxUserId()) >= MAX_ADDRESS_COUNT) {
            throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
        boolean isDefault = Boolean.TRUE.equals(address.getIsDefault());
        if (isDefault) {
            addressMapper.clearDefault(principal.wxUserId());
        }
        addressMapper.insert(address);
        return toResponse(address);
    }

    @Transactional
    public AddressResponse update(UserPrincipal principal, Long id, AddressRequest request) {
        UserAddress existing = requireOwned(principal.wxUserId(), id);
        UserAddress address = build(principal.wxUserId(), request);
        address.setId(existing.getId());
        if (Boolean.TRUE.equals(address.getIsDefault()) && !Boolean.TRUE.equals(existing.getIsDefault())) {
            addressMapper.clearDefault(principal.wxUserId());
        }
        if (addressMapper.update(address) != 1) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        return toResponse(address);
    }

    @Transactional
    public void delete(UserPrincipal principal, Long id) {
        requireOwned(principal.wxUserId(), id);
        if (addressMapper.softDelete(id, principal.wxUserId()) != 1) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
    }

    private UserAddress requireOwned(Long wxUserId, Long id) {
        return addressMapper.findByIdAndUserId(id, wxUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
    }

    private UserAddress build(Long wxUserId, AddressRequest request) {
        String contactName = request.contactName().trim();
        String phone = request.phone().trim();
        String detailAddress = request.detailAddress().trim();
        if (contactName.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "联系人不能为空");
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号格式不正确");
        }
        if (detailAddress.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "详细地址不能为空");
        }
        UserAddress address = new UserAddress();
        address.setWxUserId(wxUserId);
        address.setContactName(contactName);
        address.setPhone(phone);
        address.setDetailAddress(detailAddress);
        address.setIsDefault(Boolean.TRUE.equals(request.isDefault()));
        return address;
    }

    private AddressResponse toResponse(UserAddress address) {
        return new AddressResponse(address.getId(), address.getContactName(), address.getPhone(),
                address.getDetailAddress(), Boolean.TRUE.equals(address.getIsDefault()));
    }
}
