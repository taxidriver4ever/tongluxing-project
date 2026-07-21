package com.tongluxing.admin.mapper;

import com.tongluxing.admin.vo.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 运营后台跨交易事实表读模型；只有明确人工动作才执行条件更新。 */
@Mapper
public interface AdminTradeMapper {
    List<AdminOrderVO> pageOrders(@Param("status") String status, @Param("keyword") String keyword,
        @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
        @Param("offset") int offset, @Param("size") int size);
    long countOrders(@Param("status") String status, @Param("keyword") String keyword,
        @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    AdminOrderVO findOrder(@Param("id") Long id);
    List<AdminRefundVO> pageRefunds(@Param("status") String status, @Param("offset") int offset, @Param("size") int size);
    long countRefunds(@Param("status") String status);
    AdminRefundVO findRefund(@Param("id") Long id);
    int auditRefund(@Param("id") Long id, @Param("auditStatus") String auditStatus,
        @Param("refundStatus") String refundStatus, @Param("refundedAt") LocalDateTime refundedAt,
        @Param("now") LocalDateTime now);
    int updateOrderRefund(@Param("orderId") Long orderId, @Param("refundStatus") String refundStatus,
        @Param("orderStatus") String orderStatus, @Param("now") LocalDateTime now);
    List<AdminSettlementVO> pageSettlements(@Param("status") String status, @Param("offset") int offset, @Param("size") int size);
    long countSettlements(@Param("status") String status);
    int triggerSettlement(@Param("id") Long id, @Param("now") LocalDateTime now);
    Long settlementOrderId(@Param("id") Long id);
    int completeOrder(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);
    List<AdminVerificationRecordVO> pageVerifications(@Param("status") String status, @Param("offset") int offset, @Param("size") int size);
    long countVerifications(@Param("status") String status);
    List<AdminTransactionVO> pageTransactions(@Param("type") String type, @Param("offset") int offset, @Param("size") int size);
    long countTransactions();
    long registeredUsers();
    long activeUsers();
    long newUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    long certifiedVehicles();
    long merchants();
    long pendingMerchants();
    long activeMerchants();
    long groupbuys();
    long orders();
    long ordersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    BigDecimal paidAmount();
    BigDecimal paidBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    long verifications();
    BigDecimal commission();
    long couponOffers();
    long pendingRefunds();
    long pendingSettlements();
    List<AdminTrendPointVO> trends();
}
