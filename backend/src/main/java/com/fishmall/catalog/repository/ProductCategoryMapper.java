package com.fishmall.catalog.repository;

import com.fishmall.catalog.domain.ProductCategory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ProductCategoryMapper {
    @Select("<script>select * from product_category where deleted = 0 "
            + "<if test='status != null and status != &quot;&quot;'>and status = #{status}</if> "
            + "order by sort desc, id asc limit #{offset}, #{pageSize}</script>")
    List<ProductCategory> selectPage(@Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("select * from product_category where deleted = 0 and status = 'ACTIVE' order by sort desc, id asc")
    List<ProductCategory> findAllActive();

    @Select("<script>select count(*) from product_category where deleted = 0 "
            + "<if test='status != null and status != &quot;&quot;'>and status = #{status}</if></script>")
    long count(@Param("status") String status);

    @Select("<script>select * from product_category where id in "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<ProductCategory> findAllByIds(@Param("ids") List<Long> ids);

    @Select("select * from product_category where id = #{id} and deleted = 0")
    Optional<ProductCategory> findActiveById(@Param("id") Long id);

    @Select("select * from product_category where name = #{name} and deleted = 0 limit 1")
    Optional<ProductCategory> findActiveByName(@Param("name") String name);

    @Select("select * from product_category where name = #{name} and deleted = 0 and id != #{excludeId} limit 1")
    Optional<ProductCategory> findActiveByNameExcludingId(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Insert("insert into product_category (name, sort, status, deleted, created_at, updated_at) "
            + "values (#{name}, #{sort}, #{status}, 0, current_timestamp, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductCategory category);

    @Update("update product_category set name = #{name}, sort = #{sort}, status = #{status}, "
            + "updated_at = current_timestamp where id = #{id} and deleted = 0")
    int update(ProductCategory category);

    @Update("update product_category set deleted = 1, updated_at = current_timestamp where id = #{id} and deleted = 0")
    int softDelete(@Param("id") Long id);
}
