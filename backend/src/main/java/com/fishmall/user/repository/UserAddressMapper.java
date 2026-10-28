package com.fishmall.user.repository;

import com.fishmall.user.domain.UserAddress;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserAddressMapper {
    @Select("select * from user_address where wx_user_id = #{wxUserId} and deleted = 0 "
            + "order by is_default desc, updated_at desc, id desc")
    List<UserAddress> findByUserId(@Param("wxUserId") Long wxUserId);

    @Select("select * from user_address where id = #{id} and wx_user_id = #{wxUserId} and deleted = 0")
    Optional<UserAddress> findByIdAndUserId(@Param("id") Long id, @Param("wxUserId") Long wxUserId);

    @Select("select count(*) from user_address where wx_user_id = #{wxUserId} and deleted = 0")
    long countByUserId(@Param("wxUserId") Long wxUserId);

    @Insert("insert into user_address (wx_user_id, contact_name, phone, detail_address, is_default, deleted, "
            + "created_at, updated_at) values (#{wxUserId}, #{contactName}, #{phone}, #{detailAddress}, "
            + "#{isDefault}, 0, current_timestamp, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAddress address);

    @Update("update user_address set contact_name = #{contactName}, phone = #{phone}, "
            + "detail_address = #{detailAddress}, is_default = #{isDefault}, updated_at = current_timestamp "
            + "where id = #{id} and wx_user_id = #{wxUserId} and deleted = 0")
    int update(UserAddress address);

    @Update("update user_address set deleted = 1, is_default = 0, updated_at = current_timestamp "
            + "where id = #{id} and wx_user_id = #{wxUserId} and deleted = 0")
    int softDelete(@Param("id") Long id, @Param("wxUserId") Long wxUserId);

    @Update("update user_address set is_default = 0, updated_at = current_timestamp "
            + "where wx_user_id = #{wxUserId} and deleted = 0 and is_default = 1")
    int clearDefault(@Param("wxUserId") Long wxUserId);
}
