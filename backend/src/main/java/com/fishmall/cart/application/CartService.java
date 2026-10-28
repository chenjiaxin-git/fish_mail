package com.fishmall.cart.application;

import com.fishmall.cart.api.param.CartAddRequest;
import com.fishmall.cart.api.param.CartUpdateRequest;
import com.fishmall.cart.api.vo.CartItemResponse;
import com.fishmall.cart.domain.CartItem;
import com.fishmall.cart.repository.CartItemMapper;
import com.fishmall.catalog.domain.Product;
import com.fishmall.catalog.domain.ProductSpec;
import com.fishmall.catalog.domain.QuantityRules;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {
    private static final String REASON_OFF_SHELF = "商品已下架";
    private static final String REASON_OUT_OF_STOCK = "可用库存不足";

    private final CartItemMapper cartItemMapper;
    private final ProductSpecMapper productSpecMapper;
    private final ProductMapper productMapper;

    public CartService(CartItemMapper cartItemMapper,
                       ProductSpecMapper productSpecMapper,
                       ProductMapper productMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productSpecMapper = productSpecMapper;
        this.productMapper = productMapper;
    }

    @Transactional(readOnly = true)
    public List<CartItemResponse> list(UserPrincipal principal) {
        List<CartItemResponse> responses = new ArrayList<>();
        for (CartItem item : cartItemMapper.findByUserId(principal.wxUserId())) {
            responses.add(toResponse(item));
        }
        return responses;
    }

    @Transactional
    public CartItemResponse addItem(UserPrincipal principal, CartAddRequest request) {
        ProductSpec spec = requirePurchasableSpec(request.productSpecId());
        QuantityRules.validate(request.quantity(), spec);
        BigDecimal availableQty = availableQty(spec);
        Optional<CartItem> existing = cartItemMapper.findByUserAndSpec(principal.wxUserId(), spec.getId());
        if (existing.isPresent()) {
            CartItem item = existing.get();
            BigDecimal merged = item.getQuantity().add(request.quantity());
            QuantityRules.validate(merged, spec);
            if (merged.compareTo(availableQty) > 0) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
            }
            if (cartItemMapper.updateQuantity(item.getId(), principal.wxUserId(), merged) != 1) {
                throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
            }
            item.setQuantity(merged);
            return toResponse(item);
        }
        if (request.quantity().compareTo(availableQty) > 0) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }
        CartItem item = new CartItem();
        item.setWxUserId(principal.wxUserId());
        item.setProductSpecId(spec.getId());
        item.setQuantity(request.quantity());
        cartItemMapper.insert(item);
        return toResponse(item);
    }

    @Transactional
    public CartItemResponse updateItem(UserPrincipal principal, Long id, CartUpdateRequest request) {
        CartItem item = cartItemMapper.findByIdAndUserId(id, principal.wxUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
        ProductSpec spec = requirePurchasableSpec(item.getProductSpecId());
        QuantityRules.validate(request.quantity(), spec);
        if (request.quantity().compareTo(availableQty(spec)) > 0) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }
        if (cartItemMapper.updateQuantity(id, principal.wxUserId(), request.quantity()) != 1) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        item.setQuantity(request.quantity());
        return toResponse(item);
    }

    @Transactional
    public void deleteItem(UserPrincipal principal, Long id) {
        if (cartItemMapper.delete(id, principal.wxUserId()) != 1) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
    }

    private ProductSpec requirePurchasableSpec(Long productSpecId) {
        ProductSpec spec = productSpecMapper.findActiveById(productSpecId)
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE));
        Product product = productMapper.findActiveById(spec.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE));
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        return spec;
    }

    private BigDecimal availableQty(ProductSpec spec) {
        return spec.getPhysicalStockQty().subtract(spec.getReservedQty());
    }

    private CartItemResponse toResponse(CartItem item) {
        Optional<ProductSpec> specOption = productSpecMapper.findActiveById(item.getProductSpecId())
                .filter(value -> "ACTIVE".equals(value.getStatus()));
        if (specOption.isEmpty()) {
            return new CartItemResponse(item.getId(), item.getProductSpecId(), null, null, null, null, null,
                    null, item.getQuantity(), null, BigDecimal.ZERO, null, null, false, REASON_OFF_SHELF);
        }
        ProductSpec spec = specOption.get();
        Optional<Product> productOption = productMapper.findActiveById(spec.getProductId())
                .filter(value -> "ACTIVE".equals(value.getStatus()));
        if (productOption.isEmpty()) {
            return new CartItemResponse(item.getId(), item.getProductSpecId(), spec.getProductId(), null,
                    spec.getName(), spec.getQuantityKind(), spec.getUnit(), spec.getPriceCent(), item.getQuantity(),
                    null, BigDecimal.ZERO, spec.getMinQuantity(), spec.getStepQuantity(), false, REASON_OFF_SHELF);
        }
        Product product = productOption.get();
        BigDecimal availableQty = availableQty(spec);
        boolean purchasable = item.getQuantity().compareTo(availableQty) <= 0;
        return new CartItemResponse(item.getId(), item.getProductSpecId(), spec.getProductId(), product.getName(),
                spec.getName(), spec.getQuantityKind(), spec.getUnit(), spec.getPriceCent(), item.getQuantity(),
                product.getMainImageUrl(), availableQty, spec.getMinQuantity(), spec.getStepQuantity(),
                purchasable, purchasable ? null : REASON_OUT_OF_STOCK);
    }
}
