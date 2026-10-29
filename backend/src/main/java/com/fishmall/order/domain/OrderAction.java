package com.fishmall.order.domain;

public enum OrderAction {
    CREATE,
    TIMEOUT_CLOSE,
    PAYMENT_SUCCESS,
    USER_CONFIRM_QUOTE,
    USER_CANCEL,
    QUOTE,
    DELIVER,
    COMPLETE,
    CANCEL
}
