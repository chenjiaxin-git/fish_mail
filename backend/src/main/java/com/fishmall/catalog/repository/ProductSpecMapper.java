package com.fishmall.catalog.repository;

import com.fishmall.catalog.domain.ProductSpec;
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
public interface ProductSpecMapper {
    @Update("update product_spec set reserved_qty=reserved_qty+#{quantity},updated_at=current_timestamp(3) "
            + "where id=#{id} and physical_stock_qty-reserved_qty>=#{quantity}")
    int reservePaymentExtra(@Param("id") Long id, @Param("quantity") BigDecimal quantity);

    @Update("update product_spec set physical_stock_qty=physical_stock_qty-#{actual},reserved_qty=reserved_qty-#{reserved}, "
            + "updated_at=current_timestamp(3) where id=#{id} and physical_stock_qty>=#{actual} and reserved_qty>=#{reserved} "
            + "and physical_stock_qty-#{actual}>=reserved_qty-#{reserved}")
    int deductPaid(@Param("id") Long id, @Param("actual") BigDecimal actual, @Param("reserved") BigDecimal reserved);
    @Select("<script>select * from product_spec where deleted = 0 and status = 'ACTIVE' and product_id in "
            + "<foreach collection='productIds' item='productId' open='(' separator=',' close=')'>#{productId}</foreach> "
            + "order by product_id asc, id asc</script>")
    List<ProductSpec> findActiveByProductIds(@Param("productIds") List<Long> productIds);

    @Select("select * from product_spec where product_id = #{productId} and deleted = 0 order by id asc")
    List<ProductSpec> findActiveByProductId(@Param("productId") Long productId);

    @Select("select * from product_spec where id = #{id} and deleted = 0")
    Optional<ProductSpec> findActiveById(@Param("id") Long id);

    @Select("select * from product_spec where id = #{id} and product_id = #{productId} and deleted = 0")
    Optional<ProductSpec> findActiveByIdAndProductId(@Param("id") Long id, @Param("productId") Long productId);

    @Select("select * from product_spec where id = #{id} for update")
    Optional<ProductSpec> findForUpdateById(@Param("id") Long id);

    @Select("select coalesce(sum(reserved_qty), 0) from product_spec where product_id = #{productId} and deleted = 0 and reserved_qty > 0")
    BigDecimal sumReservedByProductId(@Param("productId") Long productId);

    @Insert("insert into product_spec (product_id, name, quantity_kind, unit, price_cent, min_quantity, step_quantity, "
            + "physical_stock_qty, reserved_qty, warning_qty, status, deleted, created_at, updated_at) "
            + "values (#{productId}, #{name}, #{quantityKind}, #{unit}, #{priceCent}, #{minQuantity}, #{stepQuantity}, "
            + "#{physicalStockQty}, 0, #{warningQty}, #{status}, 0, current_timestamp, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductSpec spec);

    @Update("update product_spec set name = #{name}, quantity_kind = #{quantityKind}, unit = #{unit}, "
            + "price_cent = #{priceCent}, min_quantity = #{minQuantity}, step_quantity = #{stepQuantity}, "
            + "warning_qty = #{warningQty}, status = #{status}, updated_at = current_timestamp "
            + "where id = #{id} and product_id = #{productId} and deleted = 0")
    int update(ProductSpec spec);

    @Update("update product_spec set deleted = 1, updated_at = current_timestamp where id = #{id} and product_id = #{productId} and deleted = 0")
    int softDelete(@Param("id") Long id, @Param("productId") Long productId);

    @Update("update product_spec set physical_stock_qty = #{targetQuantity}, updated_at = current_timestamp "
            + "where id = #{specId} and deleted = 0 and physical_stock_qty = #{beforeQuantity}")
    int updatePhysicalStock(@Param("specId") Long specId, @Param("beforeQuantity") BigDecimal beforeQuantity,
                            @Param("targetQuantity") BigDecimal targetQuantity);

    @Update("update product_spec set reserved_qty = reserved_qty - #{quantity}, updated_at = current_timestamp "
            + "where id = #{specId} and reserved_qty >= #{quantity}")
    int releaseReserved(@Param("specId") Long specId, @Param("quantity") BigDecimal quantity);

    @Update("update product_spec set reserved_qty = reserved_qty + #{quantity}, updated_at = current_timestamp "
            + "where id = #{specId} and status = 'ACTIVE' and deleted = 0 "
            + "and physical_stock_qty - reserved_qty >= #{quantity}")
    int reserve(@Param("specId") Long specId, @Param("quantity") BigDecimal quantity);

    @Update("update product_spec set physical_stock_qty = physical_stock_qty + #{quantity}, updated_at = current_timestamp "
            + "where id = #{specId} and physical_stock_qty + #{quantity} >= reserved_qty")
    int restorePhysical(@Param("specId") Long specId, @Param("quantity") BigDecimal quantity);
}
