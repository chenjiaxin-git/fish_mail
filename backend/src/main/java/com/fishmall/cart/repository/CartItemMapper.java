package com.fishmall.cart.repository;

import com.fishmall.cart.domain.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface CartItemMapper {
    @Select("select * from cart_item where wx_user_id = #{wxUserId} order by updated_at desc, id desc")
    List<CartItem> findByUserId(@Param("wxUserId") Long wxUserId);

    @Select("select * from cart_item where id = #{id} and wx_user_id = #{wxUserId}")
    Optional<CartItem> findByIdAndUserId(@Param("id") Long id, @Param("wxUserId") Long wxUserId);

    @Select("select * from cart_item where wx_user_id = #{wxUserId} and product_spec_id = #{productSpecId}")
    Optional<CartItem> findByUserAndSpec(@Param("wxUserId") Long wxUserId, @Param("productSpecId") Long productSpecId);

    @Insert("insert into cart_item (wx_user_id, product_spec_id, quantity, created_at, updated_at) "
            + "values (#{wxUserId}, #{productSpecId}, #{quantity}, current_timestamp, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CartItem item);

    @Update("update cart_item set quantity = #{quantity}, updated_at = current_timestamp "
            + "where id = #{id} and wx_user_id = #{wxUserId}")
    int updateQuantity(@Param("id") Long id, @Param("wxUserId") Long wxUserId, @Param("quantity") BigDecimal quantity);

    @Delete("delete from cart_item where id = #{id} and wx_user_id = #{wxUserId}")
    int delete(@Param("id") Long id, @Param("wxUserId") Long wxUserId);

    @Delete("<script>delete from cart_item where wx_user_id = #{wxUserId} and id in "
            + "<foreach collection='ids' item='itemId' open='(' separator=',' close=')'>#{itemId}</foreach></script>")
    int deleteByIds(@Param("wxUserId") Long wxUserId, @Param("ids") List<Long> ids);
}
