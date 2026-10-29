package com.fishmall.admin.api.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DashboardResponse(LocalDate date, long todayOrderCount, long todaySalesAmountCent,
        long pendingOrderCount, long pendingQuoteCount, long pendingDeliveryCount,
        long pendingCompletionCount, long pendingRefundCount, OffsetDateTime generatedAt) { }
