package com.fishmall.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyCalculator {
    private MoneyCalculator() {
    }

    public static long itemAmountCent(long unitPriceCent, BigDecimal quantity) {
        if (unitPriceCent < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "单价不合法");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数量不合法");
        }
        BigDecimal amount = BigDecimal.valueOf(unitPriceCent).multiply(quantity).setScale(0, RoundingMode.HALF_UP);
        try {
            return amount.longValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "金额超出范围");
        }
    }

    public static long sumCent(Iterable<Long> amounts) {
        long sum = 0L;
        for (Long amount : amounts) {
            if (amount == null || amount < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "金额不合法");
            }
            try {
                sum = Math.addExact(sum, amount);
            } catch (ArithmeticException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "金额超出范围");
            }
        }
        return sum;
    }
}
