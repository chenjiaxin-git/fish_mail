package com.fishmall.admin.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAuthLogMapper {
    @Insert("insert into admin_auth_log (username, action, result, failure_code, ip, user_agent, trace_id) "
            + "values (#{username}, #{action}, #{result}, #{failureCode}, #{ip}, #{userAgent}, #{traceId})")
    int insert(
            @Param("username") String username,
            @Param("action") String action,
            @Param("result") String result,
            @Param("failureCode") String failureCode,
            @Param("ip") String ip,
            @Param("userAgent") String userAgent,
            @Param("traceId") String traceId
    );
}
