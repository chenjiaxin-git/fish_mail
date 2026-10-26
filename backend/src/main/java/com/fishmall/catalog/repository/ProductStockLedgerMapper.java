package com.fishmall.catalog.repository;

import com.fishmall.catalog.domain.ProductStockLedger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductStockLedgerMapper {
    @Insert("insert into product_stock_ledger (product_spec_id, order_id, change_type, quantity, physical_before, physical_after, "
            + "reserved_before, reserved_after, reason, operator_type, operator_id, biz_key, created_at) "
            + "values (#{productSpecId}, #{orderId}, #{changeType}, #{quantity}, #{physicalBefore}, #{physicalAfter}, "
            + "#{reservedBefore}, #{reservedAfter}, #{reason}, #{operatorType}, #{operatorId}, #{bizKey}, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductStockLedger ledger);

    @Select("select * from product_stock_ledger where product_spec_id = #{specId} order by created_at desc, id desc limit #{limit}")
    List<ProductStockLedger> selectBySpecId(@Param("specId") Long specId, @Param("limit") int limit);
}
