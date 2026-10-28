package com.fishmall.user.repository;

import com.fishmall.user.domain.WxUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

@Mapper
public interface WxUserMapper {
    @Select("select * from wx_user where openid = #{openid}")
    Optional<WxUser> findByOpenid(@Param("openid") String openid);

    @Select("select * from wx_user where id = #{id}")
    Optional<WxUser> findById(@Param("id") Long id);

    @Insert("insert into wx_user (openid, nickname, avatar_url, status, last_login_at, created_at, updated_at) "
            + "values (#{openid}, #{nickname}, #{avatarUrl}, 'ACTIVE', current_timestamp, current_timestamp, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WxUser user);

    @Update("update wx_user set last_login_at = current_timestamp, updated_at = current_timestamp where id = #{id}")
    int touchLogin(@Param("id") Long id);
}
