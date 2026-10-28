package com.fishmall.order.repository;

import com.fishmall.order.domain.SalesOrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface SalesOrderItemMapper {
    @Select("select * from sales_order_item where order_id = #{orderId} order by id asc")
    List<SalesOrderItem> findByOrderId(@Param("orderId") Long orderId);

    @Insert("insert into sales_order_item (order_id, product_id, product_spec_id, product_name, spec_name, "
            + "quantity_kind, unit, unit_price_cent, estimated_quantity, estimated_amount_cent, product_image_url, created_at) "
            + "values (#{orderId}, #{productId}, #{productSpecId}, #{productName}, #{specName}, "
            + "#{quantityKind}, #{unit}, #{unitPriceCent}, #{estimatedQuantity}, #{estimatedAmountCent}, "
            + "#{productImageUrl}, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SalesOrderItem item);

    @Update("update sales_order_item set actual_quantity = #{actualQuantity}, final_amount_cent = #{finalAmountCent} "
            + "where id = #{id} and order_id = #{orderId}")
    int updateQuoted(@Param("id") Long id,
                     @Param("orderId") Long orderId,
                     @Param("actualQuantity") BigDecimal actualQuantity,
                     @Param("finalAmountCent") Long finalAmountCent);
}
