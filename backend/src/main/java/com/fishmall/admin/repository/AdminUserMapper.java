package com.fishmall.admin.repository;

import com.fishmall.admin.domain.AdminUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

@Mapper
public interface AdminUserMapper {
    @Select("select count(*) from admin_user")
    int count();

    @Select("select * from admin_user where id = #{id}")
    Optional<AdminUser> findById(@Param("id") Long id);

    @Select("select * from admin_user where username = #{username}")
    Optional<AdminUser> findByUsername(@Param("username") String username);

    @Insert("insert into admin_user (id, username, password_hash, token_version, status, created_at, updated_at) "
            + "values (#{id}, #{username}, #{passwordHash}, #{tokenVersion}, #{status}, current_timestamp, current_timestamp)")
    @Options(useGeneratedKeys = false)
    int insert(AdminUser adminUser);

    @Update("update admin_user set last_login_at = current_timestamp, updated_at = current_timestamp where id = #{id}")
    int updateLastLogin(@Param("id") Long id);

    @Update("update admin_user set password_hash = #{passwordHash}, token_version = token_version + 1, "
            + "updated_at = current_timestamp where id = #{id} and token_version = #{expectedTokenVersion}")
    int updatePassword(
            @Param("id") Long id,
            @Param("passwordHash") String passwordHash,
            @Param("expectedTokenVersion") Long expectedTokenVersion
    );
}
