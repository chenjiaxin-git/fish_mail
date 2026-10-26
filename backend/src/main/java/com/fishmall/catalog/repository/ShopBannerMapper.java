package com.fishmall.catalog.repository;

import com.fishmall.catalog.domain.ShopBanner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShopBannerMapper {
    @Select("select * from shop_banner where deleted = 0 and status = 'ACTIVE' order by sort asc, id asc")
    List<ShopBanner> findAllActive();
}
