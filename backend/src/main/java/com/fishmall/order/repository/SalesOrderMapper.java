package com.fishmall.order.repository;

import com.fishmall.order.domain.SalesOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface SalesOrderMapper {
    String TIMEOUT_DUE = " ((status='CREATED' and created_at &lt;= #{createdCutoff}) "
            + "or (status in ('QUOTED','PENDING_PAYMENT') and pay_deadline_at &lt;= #{now})) ";

    @Select("<script>select * from sales_order where id &gt; #{afterId} and " + TIMEOUT_DUE
            + "and (timeout_next_attempt_at is null or timeout_next_attempt_at &lt;= #{now}) order by id asc limit #{limit}</script>")
    List<SalesOrder> timeoutCandidates(@Param("afterId") long afterId, @Param("now") LocalDateTime now,
                                       @Param("createdCutoff") LocalDateTime createdCutoff, @Param("limit") int limit);

    @Update("<script>update sales_order set status='CLOSED',closed_at=#{now},version=version+1,"
            + "timeout_next_attempt_at=null,updated_at=current_timestamp(3) where id=#{id} and version=#{version} "
            + "and status=#{status} and " + TIMEOUT_DUE + "</script>")
    int closeTimedOut(@Param("id") Long id, @Param("version") Long version, @Param("status") String status,
                       @Param("now") LocalDateTime now, @Param("createdCutoff") LocalDateTime createdCutoff);

    @Update("<script>update sales_order set timeout_next_attempt_at=#{next},timeout_attempt_count=least(timeout_attempt_count+1,32) "
            + "where id=#{id} and version=#{version} and status=#{status} and " + TIMEOUT_DUE + "</script>")
    int retryTimeout(@Param("id") Long id, @Param("version") Long version, @Param("status") String status,
                      @Param("now") LocalDateTime now, @Param("createdCutoff") LocalDateTime createdCutoff,
                      @Param("next") LocalDateTime next);
    @Update("update sales_order set status='PAID_WAIT_DELIVERY',paid_at=#{paidAt},version=version+1,updated_at=current_timestamp(3) "
            + "where id=#{id} and status='PENDING_PAYMENT' and version=#{version}")
    int markPaid(@Param("id") Long id, @Param("version") Long version, @Param("paidAt") LocalDateTime paidAt);
    @Select("select * from sales_order where id = #{id} and wx_user_id = #{userId}")
    Optional<SalesOrder> findByUser(@Param("id") Long id, @Param("userId") Long userId);

    @Select("select * from sales_order where id = #{id} and wx_user_id = #{userId} for update")
    Optional<SalesOrder> findByUserForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    String USER_FILTER = " where wx_user_id = #{userId} <if test='statuses.size() > 0'> and status in "
            + "<foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach></if> ";

    @Select("<script>select * from sales_order" + USER_FILTER
            + "order by created_at desc, id desc limit #{offset}, #{limit}</script>")
    List<SalesOrder> selectUserPage(@Param("userId") Long userId, @Param("statuses") List<String> statuses,
                                    @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>select count(*) from sales_order" + USER_FILTER + "</script>")
    long countUser(@Param("userId") Long userId, @Param("statuses") List<String> statuses);

    @Update("update sales_order set status = 'PENDING_PAYMENT', version = version + 1, updated_at = current_timestamp(3) "
            + "where id = #{id} and wx_user_id = #{userId} and status = 'QUOTED' "
            + "and quote_version = #{quoteVersion} and version = #{version} and pay_deadline_at > #{now}")
    int confirmUserQuote(@Param("id") Long id, @Param("userId") Long userId,
                         @Param("quoteVersion") int quoteVersion, @Param("version") Long version,
                         @Param("now") LocalDateTime now);

    @Update("update sales_order set status = 'CANCELLED', cancelled_at = current_timestamp(3), "
            + "cancel_reason = #{reason}, refund_status = 'NONE', version = version + 1, updated_at = current_timestamp(3) "
            + "where id = #{id} and wx_user_id = #{userId} and status in ('CREATED','QUOTED','PENDING_PAYMENT') and version = #{version}")
    int cancelUser(@Param("id") Long id, @Param("userId") Long userId,
                   @Param("version") Long version, @Param("reason") String reason);

    @Select("select * from sales_order where id = #{id}")
    Optional<SalesOrder> findById(@Param("id") Long id);

    @Select("select * from sales_order where id = #{id} for update")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    @Select("select * from sales_order where wx_user_id = #{wxUserId} and idempotency_key = #{idempotencyKey} for update")
    Optional<SalesOrder> findByUserIdAndIdempotencyKey(@Param("wxUserId") Long wxUserId,
                                                      @Param("idempotencyKey") String idempotencyKey);

    @Insert("insert into sales_order (order_no, wx_user_id, status, source, remark, address_snapshot, "
            + "estimated_amount_cent, currency, quote_version, refund_status, idempotency_key, created_at, updated_at) "
            + "values (#{orderNo}, #{wxUserId}, 'CREATED', 'MINI_PROGRAM', #{remark}, #{addressSnapshot}, "
            + "#{estimatedAmountCent}, 'CNY', 0, 'NONE', #{idempotencyKey}, current_timestamp, current_timestamp)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SalesOrder order);

    @Select("""
            <script>
            select * from sales_order
            <where>
              <if test='status != null'>and status = #{status}</if>
              <if test='orderNo != null and orderNo != ""'>and order_no = #{orderNo}</if>
              <if test='phone != null and phone != ""'>and json_unquote(json_extract(address_snapshot, '$.phone')) = #{phone}</if>
              <if test='refundStatus != null'>and refund_status = #{refundStatus}</if>
              <if test='startCreatedAt != null'>and created_at &gt;= #{startCreatedAt}</if>
              <if test='endCreatedAt != null'>and created_at &lt; #{endCreatedAt}</if>
            </where>
            order by created_at desc, id desc
            limit #{offset}, #{limit}
            </script>
            """)
    List<SalesOrder> selectPage(@Param("status") String status,
                                @Param("orderNo") String orderNo,
                                @Param("phone") String phone,
                                @Param("refundStatus") String refundStatus,
                                @Param("startCreatedAt") LocalDateTime startCreatedAt,
                                @Param("endCreatedAt") LocalDateTime endCreatedAt,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    @Select("""
            <script>
            select count(*) from sales_order
            <where>
              <if test='status != null'>and status = #{status}</if>
              <if test='orderNo != null and orderNo != ""'>and order_no = #{orderNo}</if>
              <if test='phone != null and phone != ""'>and json_unquote(json_extract(address_snapshot, '$.phone')) = #{phone}</if>
              <if test='refundStatus != null'>and refund_status = #{refundStatus}</if>
              <if test='startCreatedAt != null'>and created_at &gt;= #{startCreatedAt}</if>
              <if test='endCreatedAt != null'>and created_at &lt; #{endCreatedAt}</if>
            </where>
            </script>
            """)
    long count(@Param("status") String status,
               @Param("orderNo") String orderNo,
               @Param("phone") String phone,
                                @Param("refundStatus") String refundStatus,
               @Param("startCreatedAt") LocalDateTime startCreatedAt,
               @Param("endCreatedAt") LocalDateTime endCreatedAt);

    @Select("""
            <script>
            select * from sales_order
            <where>
              <if test='status != null'>and status = #{status}</if>
              <if test='orderNo != null and orderNo != ""'>and order_no = #{orderNo}</if>
              <if test='phone != null and phone != ""'>and json_unquote(json_extract(address_snapshot, '$.phone')) = #{phone}</if>
              <if test='refundStatus != null'>and refund_status = #{refundStatus}</if>
              and created_at &gt;= #{startCreatedAt}
              and created_at &lt; #{endCreatedAt}
            </where>
            order by created_at desc, id desc
            </script>
            """)
    List<SalesOrder> selectForExport(@Param("status") String status,
                                     @Param("orderNo") String orderNo,
                                     @Param("phone") String phone,
                                @Param("refundStatus") String refundStatus,
                                     @Param("startCreatedAt") LocalDateTime startCreatedAt,
                                     @Param("endCreatedAt") LocalDateTime endCreatedAt);

    @Update("""
            update sales_order
            set status = 'QUOTED', final_amount_cent = #{finalAmountCent}, quote_version = #{nextQuoteVersion}, timeout_next_attempt_at = null,
                quoted_at = current_timestamp, pay_deadline_at = #{payDeadlineAt}, version = version + 1,
                updated_at = current_timestamp
            where id = #{id} and status in ('CREATED', 'QUOTED')
              and quote_version = #{quoteVersion} and version = #{version}
            """)
    int updateQuote(@Param("id") Long id,
                    @Param("finalAmountCent") Long finalAmountCent,
                    @Param("quoteVersion") int quoteVersion,
                    @Param("nextQuoteVersion") int nextQuoteVersion,
                    @Param("payDeadlineAt") LocalDateTime payDeadlineAt,
                    @Param("version") Long version);

    @Update("""
            update sales_order set status = 'DELIVERING', delivered_at = current_timestamp,
                version = version + 1, updated_at = current_timestamp
            where id = #{id} and status = 'PAID_WAIT_DELIVERY' and version = #{version}
            """)
    int updateDelivering(@Param("id") Long id, @Param("version") Long version);

    @Update("""
            update sales_order set status = 'COMPLETED', completed_at = current_timestamp,
                version = version + 1, updated_at = current_timestamp
            where id = #{id} and status = 'DELIVERING' and version = #{version}
            """)
    int updateCompleted(@Param("id") Long id, @Param("version") Long version);

    @Update("""
            update sales_order set status = 'CANCELLED', cancelled_at = current_timestamp,
                cancel_reason = #{cancelReason}, refund_status = #{refundStatus},
                version = version + 1, updated_at = current_timestamp
            where id = #{id} and status in ('CREATED', 'QUOTED', 'PENDING_PAYMENT', 'PAID_WAIT_DELIVERY')
              and version = #{version}
            """)
    int updateCancelled(@Param("id") Long id,
                        @Param("cancelReason") String cancelReason,
                        @Param("refundStatus") String refundStatus,
                        @Param("version") Long version);

    @Update("""
            update sales_order set refund_status = 'MANUAL_REFUNDED', refund_reference = #{refundReference},
                version = version + 1, updated_at = current_timestamp
            where id = #{id} and status = 'CANCELLED' and refund_status = 'REQUIRED' and version = #{version}
            """)
    int updateRefundConfirmed(@Param("id") Long id,
                              @Param("refundReference") String refundReference,
                              @Param("version") Long version);
}
