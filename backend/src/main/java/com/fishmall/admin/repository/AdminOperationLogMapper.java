package com.fishmall.admin.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminOperationLogMapper {
    @Insert("insert into admin_operation_log (admin_id, admin_username, module, action, biz_id, result, reason, trace_id, created_at) "
            + "values (#{adminId}, #{adminUsername}, #{module}, #{action}, #{bizId}, #{result}, #{reason}, #{traceId}, current_timestamp)")
    int insert(
            @Param("adminId") Long adminId,
            @Param("adminUsername") String adminUsername,
            @Param("module") String module,
            @Param("action") String action,
            @Param("bizId") String bizId,
            @Param("result") String result,
            @Param("reason") String reason,
            @Param("traceId") String traceId
    );
}
