package com.fishmall.catalog.repository;

import com.fishmall.catalog.domain.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ProductMapper {
    @Select("<script>select * from product where deleted = 0 "
            + "<if test='keyword != null and keyword != &quot;&quot;'>and name like concat('%', #{keyword}, '%')</if> "
            + "<if test='categoryId != null'>and category_id = #{categoryId}</if> "
            + "<if test='status != null and status != &quot;&quot;'>and status = #{status}</if> "
            + "order by updated_at desc, id desc limit #{offset}, #{pageSize}</script>")
    List<Product> selectPage(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
                             @Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("<script>select count(*) from product where deleted = 0 "
            + "<if test='keyword != null and keyword != &quot;&quot;'>and name like concat('%', #{keyword}, '%')</if> "
            + "<if test='categoryId != null'>and category_id = #{categoryId}</if> "
            + "<if test='status != null and status != &quot;&quot;'>and status = #{status}</if></script>")
    long count(@Param("keyword") String keyword, @Param("categoryId") Long categoryId, @Param("status") String status);

    @Select("select * from product where id = #{id} and deleted = 0")
    Optional<Product> findActiveById(@Param("id") Long id);

    @Insert("insert into product (category_id, name, subtitle, description, main_image_url, status, deleted, created_at, updated_at) "
            + "values (#{categoryId}, #{name}, #{subtitle}, #{description}, #{mainImageUrl}, #{status}, 0, current_timestamp, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);

    @Update("update product set category_id = #{categoryId}, name = #{name}, subtitle = #{subtitle}, "
            + "description = #{description}, main_image_url = #{mainImageUrl}, status = #{status}, "
            + "updated_at = current_timestamp where id = #{id} and deleted = 0")
    int update(Product product);

    @Update("update product set deleted = 1, updated_at = current_timestamp where id = #{id} and deleted = 0")
    int softDelete(@Param("id") Long id);

    @Select("select count(*) from product where category_id = #{categoryId} and deleted = 0")
    long countActiveByCategoryId(@Param("categoryId") Long categoryId);
}
