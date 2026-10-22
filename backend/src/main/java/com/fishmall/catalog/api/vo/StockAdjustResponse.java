package com.fishmall.catalog.api.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockAdjustResponse(
        Long productSpecId,
        BigDecimal beforeQuantity,
        BigDecimal afterQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        StockLedgerResponse ledger
) {
    public record StockLedgerResponse(
            Long id,
            String changeType,
            BigDecimal quantity,
            BigDecimal physicalBefore,
            BigDecimal physicalAfter,
            BigDecimal reservedBefore,
            BigDecimal reservedAfter,
            String reason,
            String bizKey,
            LocalDateTime createdAt
    ) {
    }
}
