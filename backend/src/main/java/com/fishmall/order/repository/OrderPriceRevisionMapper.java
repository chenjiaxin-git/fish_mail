package com.fishmall.order.repository;

import com.fishmall.order.domain.OrderPriceRevision;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderPriceRevisionMapper {
    @Insert("insert into order_price_revision (order_id, revision_no, previous_amount_cent, final_amount_cent, "
            + "reason, items_snapshot, adjusted_by_type, adjusted_by_id, created_at) "
            + "values (#{orderId}, #{revisionNo}, #{previousAmountCent}, #{finalAmountCent}, #{reason}, "
            + "#{itemsSnapshot}, #{adjustedByType}, #{adjustedById}, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderPriceRevision revision);

    @Select("select * from order_price_revision where order_id = #{orderId} order by revision_no asc")
    List<OrderPriceRevision> findByOrderId(@Param("orderId") Long orderId);
}
