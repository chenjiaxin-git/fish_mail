package com.fishmall.common;

public enum ErrorCode {
    AUTH_REQUIRED("AUTH_REQUIRED", "请先登录", 401),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "用户名或密码错误", 401),
    AUTH_TOKEN_REVOKED("AUTH_TOKEN_REVOKED", "登录已失效，请重新登录", 401),
    AUTH_LOGIN_FAILED("AUTH_LOGIN_FAILED", "微信登录失败，请稍后重试", 401),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "无权访问资源", 403),
    PASSWORD_INVALID("PASSWORD_INVALID", "新密码不符合要求", 400),
    PASSWORD_CURRENT_MISMATCH("PASSWORD_CURRENT_MISMATCH", "当前密码错误", 400),
    PASSWORD_CONFIRM_MISMATCH("PASSWORD_CONFIRM_MISMATCH", "两次输入的新密码不一致", 400),
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "商品不存在", 404),
    PRODUCT_HAS_RESERVATION("PRODUCT_HAS_RESERVATION", "商品仍有库存预占，不能删除", 409),
    PRODUCT_SPEC_NOT_FOUND("PRODUCT_SPEC_NOT_FOUND", "商品规格不存在", 404),
    PRODUCT_SPEC_IN_USE("PRODUCT_SPEC_IN_USE", "商品规格仍有库存预占，不能删除或修改计价属性", 409),
    CATEGORY_NOT_FOUND("CATEGORY_NOT_FOUND", "商品分类不存在", 404),
    CATEGORY_HAS_PRODUCTS("CATEGORY_HAS_PRODUCTS", "分类下仍有商品，不能删除", 409),
    CATEGORY_NAME_DUPLICATE("CATEGORY_NAME_DUPLICATE", "分类名称已存在", 409),
    FILE_TYPE_INVALID("FILE_TYPE_INVALID", "仅支持 jpg、jpeg、png、webp 图片", 400),
    FILE_TOO_LARGE("FILE_TOO_LARGE", "图片不能超过 5 MB", 400),
    FILE_WRITE_FAILED("FILE_WRITE_FAILED", "图片保存失败", 500),
    STOCK_VERSION_CONFLICT("STOCK_VERSION_CONFLICT", "库存已被其他操作修改，请刷新后重试", 409),
    STOCK_INSUFFICIENT("STOCK_INSUFFICIENT", "可用库存不足", 409),
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "订单不存在", 404),
    ORDER_STATE_INVALID("ORDER_STATE_INVALID", "订单当前状态不允许此操作", 409),
    QUOTE_VERSION_CONFLICT("QUOTE_VERSION_CONFLICT", "订单已发生新改价，请刷新后重试", 409),
    ORDER_QUOTE_ITEMS_INVALID("ORDER_QUOTE_ITEMS_INVALID", "改价商品项不完整或重复", 400),
    ORDER_QUOTE_AMOUNT_MISMATCH("ORDER_QUOTE_AMOUNT_MISMATCH", "最终总额与订单项金额之和不一致", 400),
    ADDRESS_NOT_FOUND("ADDRESS_NOT_FOUND", "收货地址不存在", 404),
    ADDRESS_LIMIT_EXCEEDED("ADDRESS_LIMIT_EXCEEDED", "收货地址不能超过 20 个", 409),
    CART_ITEM_NOT_FOUND("CART_ITEM_NOT_FOUND", "购物车条目不存在", 404),
    PRODUCT_NOT_AVAILABLE("PRODUCT_NOT_AVAILABLE", "商品当前不可购买", 409),
    REFUND_NOT_REQUIRED("REFUND_NOT_REQUIRED", "订单当前不需要登记人工退款", 409),
    REFUND_REFERENCE_REQUIRED("REFUND_REFERENCE_REQUIRED", "人工退款凭证不能为空", 400),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败", 400),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务暂不可用", 500);

    private final String code;
    private final String message;

    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
