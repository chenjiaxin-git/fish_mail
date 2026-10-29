package com.fishmall.order.repository;

import com.fishmall.order.domain.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Options;
import java.util.Optional;

import java.util.List;

@Mapper
public interface PaymentRecordMapper {
    @Select("select * from payment_record where order_id=#{id}")
    Optional<PaymentRecord> current(@Param("id") Long id);
    @Select("select * from payment_record where order_id=#{id} for update")
    Optional<PaymentRecord> lock(@Param("id") Long id);
    @Select("select * from payment_record where out_trade_no=#{no}")
    Optional<PaymentRecord> byTradeNo(@Param("no") String no);
    @Insert("insert into payment_record(order_id,out_trade_no,state,amount_cent,quote_version,appid,mchid,payer_openid,expires_at,phase) "
            + "values(#{orderId},#{outTradeNo},'INIT',#{amountCent},#{quoteVersion},#{appid},#{mchid},#{payerOpenid},#{expiresAt},'NEW')")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(PaymentRecord record);
    @Update("update payment_record set phase=#{phase},operation_token=#{operationToken},operation_until=#{operationUntil},"
            + "sync_after=#{syncAfter},prepay_id=#{prepayId},updated_at=current_timestamp(3) where id=#{id}")
    int saveOperation(PaymentRecord record);
    @Update("update payment_record set state='SUCCESS',phase='SUCCESS',transaction_id=#{transactionId},trade_state='SUCCESS',"
            + "paid_at=#{paidAt},operation_token=null,operation_until=null,updated_at=current_timestamp(3) where id=#{id} and state<>'SUCCESS'")
    int markSuccess(PaymentRecord record);
    @Select("select * from payment_record where order_id = #{orderId} order by created_at desc, id desc")
    List<PaymentRecord> findByOrderId(@Param("orderId") Long orderId);
}
