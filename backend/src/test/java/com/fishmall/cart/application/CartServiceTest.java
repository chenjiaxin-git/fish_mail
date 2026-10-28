package com.fishmall.cart.application;

import com.fishmall.cart.api.param.CartAddRequest;
import com.fishmall.cart.api.param.CartUpdateRequest;
import com.fishmall.cart.api.vo.CartItemResponse;
import com.fishmall.cart.domain.CartItem;
import com.fishmall.cart.repository.CartItemMapper;
import com.fishmall.catalog.domain.Product;
import com.fishmall.catalog.domain.ProductSpec;
import com.fishmall.catalog.repository.ProductMapper;
import com.fishmall.catalog.repository.ProductSpecMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    @Mock private CartItemMapper cartItemMapper;
    @Mock private ProductSpecMapper productSpecMapper;
    @Mock private ProductMapper productMapper;

    private CartService service;
    private final UserPrincipal principal = new UserPrincipal(7L);

    @BeforeEach
    void setUp() {
        service = new CartService(cartItemMapper, productSpecMapper, productMapper);
    }

    @Test
    void addItemCreatesNewRow() {
        stubPurchasableSpec();
        when(cartItemMapper.findByUserAndSpec(7L, 20L)).thenReturn(Optional.empty());
        when(cartItemMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, CartItem.class).setId(5L);
            return 1;
        });

        CartItemResponse response = service.addItem(principal, new CartAddRequest(20L, new BigDecimal("1.500")));

        assertThat(response.quantity()).isEqualByComparingTo("1.500");
        assertThat(response.purchasable()).isTrue();
        verify(cartItemMapper).insert(argThat(item -> item.getQuantity().compareTo(new BigDecimal("1.500")) == 0));
    }

    @Test
    void addItemMergesExistingQuantity() {
        stubPurchasableSpec();
        CartItem existing = cartItem(5L, new BigDecimal("1.000"));
        when(cartItemMapper.findByUserAndSpec(7L, 20L)).thenReturn(Optional.of(existing));
        when(cartItemMapper.updateQuantity(5L, 7L, new BigDecimal("2.500"))).thenReturn(1);

        CartItemResponse response = service.addItem(principal, new CartAddRequest(20L, new BigDecimal("1.500")));

        assertThat(response.quantity()).isEqualByComparingTo("2.500");
        verify(cartItemMapper, never()).insert(any());
    }

    @Test
    void addItemRejectsOffGridQuantity() {
        stubPurchasableSpec();

        assertThatThrownBy(() -> service.addItem(principal, new CartAddRequest(20L, new BigDecimal("1.200"))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void addItemRejectsStockInsufficient() {
        stubPurchasableSpec();
        when(cartItemMapper.findByUserAndSpec(7L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(principal, new CartAddRequest(20L, new BigDecimal("5.500"))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
    }

    @Test
    void addItemRejectsOffShelfSpec() {
        when(productSpecMapper.findActiveById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(principal, new CartAddRequest(20L, new BigDecimal("1.500"))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_AVAILABLE);
    }

    @Test
    void listMarksUnavailableWhenStockShort() {
        stubPurchasableSpec();
        CartItem item = cartItem(5L, new BigDecimal("5.500"));
        when(cartItemMapper.findByUserId(7L)).thenReturn(List.of(item));

        List<CartItemResponse> responses = service.list(principal);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).purchasable()).isFalse();
        assertThat(responses.get(0).unavailableReason()).isEqualTo("可用库存不足");
    }

    @Test
    void updateItemRejectsForeignRow() {
        when(cartItemMapper.findByIdAndUserId(5L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateItem(principal, 5L, new CartUpdateRequest(new BigDecimal("1.500"))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    void deleteItemRejectsMissingRow() {
        when(cartItemMapper.delete(5L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteItem(principal, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    private void stubPurchasableSpec() {
        ProductSpec spec = spec();
        Product product = product();
        when(productSpecMapper.findActiveById(20L)).thenReturn(Optional.of(spec));
        when(productMapper.findActiveById(10L)).thenReturn(Optional.of(product));
    }

    private ProductSpec spec() {
        ProductSpec spec = new ProductSpec();
        spec.setId(20L);
        spec.setProductId(10L);
        spec.setName("按斤");
        spec.setQuantityKind("WEIGHT");
        spec.setUnit("kg");
        spec.setPriceCent(3000L);
        spec.setMinQuantity(new BigDecimal("1.000"));
        spec.setStepQuantity(new BigDecimal("0.500"));
        spec.setPhysicalStockQty(new BigDecimal("5.500"));
        spec.setReservedQty(new BigDecimal("0.500"));
        spec.setStatus("ACTIVE");
        return spec;
    }

    private Product product() {
        Product product = new Product();
        product.setId(10L);
        product.setName("草鱼");
        product.setMainImageUrl("/uploads/products/uuid.jpg");
        product.setStatus("ACTIVE");
        return product;
    }

    private CartItem cartItem(Long id, BigDecimal quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setWxUserId(7L);
        item.setProductSpecId(20L);
        item.setQuantity(quantity);
        return item;
    }
}
