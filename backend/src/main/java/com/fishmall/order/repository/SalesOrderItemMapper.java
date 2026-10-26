package com.fishmall.order.repository;

import com.fishmall.order.domain.SalesOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface SalesOrderItemMapper {
    @Select("select * from sales_order_item where order_id = #{orderId} order by id asc")
    List<SalesOrderItem> findByOrderId(@Param("orderId") Long orderId);

    @Update("update sales_order_item set actual_quantity = #{actualQuantity}, final_amount_cent = #{finalAmountCent} "
            + "where id = #{id} and order_id = #{orderId}")
    int updateQuoted(@Param("id") Long id,
                     @Param("orderId") Long orderId,
                     @Param("actualQuantity") BigDecimal actualQuantity,
                     @Param("finalAmountCent") Long finalAmountCent);
}
