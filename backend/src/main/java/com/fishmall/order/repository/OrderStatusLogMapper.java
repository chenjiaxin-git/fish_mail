package com.fishmall.order.repository;

import com.fishmall.order.domain.OrderStatusLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderStatusLogMapper {
    @Insert("insert into order_status_log (order_id, from_status, to_status, operator_type, operator_id, "
            + "reason, source, created_at) values (#{orderId}, #{fromStatus}, #{toStatus}, #{operatorType}, "
            + "#{operatorId}, #{reason}, #{source}, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderStatusLog statusLog);

    @Select("select * from order_status_log where order_id = #{orderId} order by created_at desc, id desc")
    List<OrderStatusLog> findByOrderId(@Param("orderId") Long orderId);
}
