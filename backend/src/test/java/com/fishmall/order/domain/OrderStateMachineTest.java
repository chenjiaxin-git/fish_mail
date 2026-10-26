package com.fishmall.order.domain;

import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {
    @Test
    void allowsConfiguredTransitions() {
        assertThatCode(() -> OrderStateMachine.validate(OrderStatus.CREATED, OrderAction.QUOTE))
                .doesNotThrowAnyException();
        assertThatCode(() -> OrderStateMachine.validate(OrderStatus.QUOTED, OrderAction.QUOTE))
                .doesNotThrowAnyException();
        assertThatCode(() -> OrderStateMachine.validate(OrderStatus.PAID_WAIT_DELIVERY, OrderAction.CANCEL))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidTransitions() {
        assertThatThrownBy(() -> OrderStateMachine.validate(OrderStatus.COMPLETED, OrderAction.CANCEL))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.ORDER_STATE_INVALID);
        assertThatThrownBy(() -> OrderStateMachine.validate(OrderStatus.CREATED, OrderAction.COMPLETE))
                .isInstanceOf(BusinessException.class);
    }
}
