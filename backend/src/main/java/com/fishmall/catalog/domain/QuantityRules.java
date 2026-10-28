package com.fishmall.catalog.domain;

import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;

import java.math.BigDecimal;

public final class QuantityRules {
    private QuantityRules() {
    }

    public static void validate(BigDecimal quantity, ProductSpec spec) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数量必须大于 0");
        }
        if (quantity.stripTrailingZeros().scale() > 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数量最多保留三位小数");
        }
        if ("PIECE".equals(spec.getQuantityKind()) && quantity.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "件数规格的数量必须为整数");
        }
        if (quantity.compareTo(spec.getMinQuantity()) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数量不能低于起购量");
        }
        BigDecimal remainder = quantity.subtract(spec.getMinQuantity()).remainder(spec.getStepQuantity());
        if (remainder.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数量不符合步进要求");
        }
    }
}
