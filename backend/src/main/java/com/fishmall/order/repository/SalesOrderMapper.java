package com.fishmall.order.repository;

import com.fishmall.order.domain.SalesOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface SalesOrderMapper {
    @Select("select * from sales_order where id = #{id}")
    Optional<SalesOrder> findById(@Param("id") Long id);

    @Select("select * from sales_order where id = #{id} for update")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    @Select("""
            <script>
            select * from sales_order
            <where>
              <if test='status != null'>and status = #{status}</if>
              <if test='orderNo != null and orderNo != ""'>and order_no = #{orderNo}</if>
              <if test='phone != null and phone != ""'>and json_unquote(json_extract(address_snapshot, '$.phone')) = #{phone}</if>
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
              <if test='startCreatedAt != null'>and created_at &gt;= #{startCreatedAt}</if>
              <if test='endCreatedAt != null'>and created_at &lt; #{endCreatedAt}</if>
            </where>
            </script>
            """)
    long count(@Param("status") String status,
               @Param("orderNo") String orderNo,
               @Param("phone") String phone,
               @Param("startCreatedAt") LocalDateTime startCreatedAt,
               @Param("endCreatedAt") LocalDateTime endCreatedAt);

    @Select("""
            <script>
            select * from sales_order
            <where>
              <if test='status != null'>and status = #{status}</if>
              <if test='orderNo != null and orderNo != ""'>and order_no = #{orderNo}</if>
              <if test='phone != null and phone != ""'>and json_unquote(json_extract(address_snapshot, '$.phone')) = #{phone}</if>
              and created_at &gt;= #{startCreatedAt}
              and created_at &lt; #{endCreatedAt}
            </where>
            order by created_at desc, id desc
            </script>
            """)
    List<SalesOrder> selectForExport(@Param("status") String status,
                                     @Param("orderNo") String orderNo,
                                     @Param("phone") String phone,
                                     @Param("startCreatedAt") LocalDateTime startCreatedAt,
                                     @Param("endCreatedAt") LocalDateTime endCreatedAt);

    @Update("""
            update sales_order
            set status = 'QUOTED', final_amount_cent = #{finalAmountCent}, quote_version = #{nextQuoteVersion},
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
