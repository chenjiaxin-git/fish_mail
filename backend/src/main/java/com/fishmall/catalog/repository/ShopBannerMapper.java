package com.fishmall.catalog.repository;

import com.fishmall.catalog.domain.ShopBanner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.*;
import com.fishmall.catalog.api.vo.AdminBannerResponse;
import java.util.Optional;

import java.util.List;

@Mapper
public interface ShopBannerMapper {
    String ADMIN_COLUMNS="select b.id,b.title,b.image_url,b.jump_type,b.jump_target_id,b.sort,b.status, "
            + "case when b.jump_type='NONE' then 1 when b.jump_type='PRODUCT' then (p.id is not null and p.deleted=0 and p.status='ACTIVE') "
            + "else (c.id is not null and c.deleted=0 and c.status='ACTIVE') end as target_available, "
            + "coalesce(p.name,c.name) as target_name,b.created_at,b.updated_at from shop_banner b "
            + "left join product p on b.jump_type='PRODUCT' and p.id=b.jump_target_id "
            + "left join product_category c on b.jump_type='CATEGORY' and c.id=b.jump_target_id ";
    @Select("<script>"+ADMIN_COLUMNS+" where b.deleted=0 <if test='status!=null'>and b.status=#{status}</if> "
            + "order by b.sort,b.id limit #{offset},#{limit}</script>")
    List<AdminBannerResponse> page(@Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
    @Select("<script>select count(*) from shop_banner where deleted=0 <if test='status!=null'>and status=#{status}</if></script>")
    long count(@Param("status") String status);
    @Select(ADMIN_COLUMNS+"where b.id=#{id} and b.deleted=0")
    Optional<AdminBannerResponse> detail(@Param("id") Long id);
    @Insert("insert into shop_banner(title,image_url,jump_type,jump_target_id,sort,status) values(#{title},#{imageUrl},#{jumpType},#{jumpTargetId},#{sort},#{status})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(ShopBanner banner);
    @Update("update shop_banner set title=#{title},image_url=#{imageUrl},jump_type=#{jumpType},jump_target_id=#{jumpTargetId},sort=#{sort},status=#{status},updated_at=current_timestamp(3) "
            + "where id=#{id} and deleted=0")
    int update(ShopBanner banner);
    @Update("update shop_banner set deleted=1,updated_at=current_timestamp(3) where id=#{id} and deleted=0")
    int delete(@Param("id") Long id);
    @Select("select * from shop_banner where deleted = 0 and status = 'ACTIVE' order by sort asc, id asc")
    List<ShopBanner> findAllActive();
}
