package com.fishmall.order.repository;

import com.fishmall.order.domain.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentRecordMapper {
    @Select("select * from payment_record where order_id = #{orderId} order by created_at desc, id desc")
    List<PaymentRecord> findByOrderId(@Param("orderId") Long orderId);
}
