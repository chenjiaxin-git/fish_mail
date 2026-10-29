package com.fishmall.admin.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface DashboardMapper {
    record OrderCounts(long today, long quote, long delivery, long completion, long refund) { }
    @Select("select coalesce(sum(created_at>=#{start} and created_at<#{end}),0) as today, "
            + "coalesce(sum(status='CREATED'),0) as quote, coalesce(sum(status='PAID_WAIT_DELIVERY'),0) as delivery, "
            + "coalesce(sum(status='DELIVERING'),0) as completion, "
            + "coalesce(sum(status='CANCELLED' and refund_status='REQUIRED'),0) as refund from sales_order")
    OrderCounts counts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    @Select("select coalesce(sum(amount_cent),0) from payment_record where state='SUCCESS' and paid_at>=#{start} and paid_at<#{end}")
    BigDecimal sales(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
