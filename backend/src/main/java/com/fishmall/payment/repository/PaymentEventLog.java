package com.fishmall.payment.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentEventLog {
    @Insert("insert into payment_event_log(event_key,out_trade_no,result,encrypted_notification) values(#{key},#{no},#{result},#{body}) "
            + "on duplicate key update result=values(result)")
    int save(@Param("key") String key, @Param("no") String no, @Param("result") String result, @Param("body") String body);
}
