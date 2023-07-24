package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlRecharge;
import com.seektop.fund.vo.*;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 充值记录
 */
public interface GlRechargeMapper extends Mapper<GlRecharge> {

    @Select("select count(*) as successTimes from gl_recharge where status = 1 and user_id = #{userId}")
    Integer countUserSuccessTimes(@Param("userId") Integer userId);

    @Select("select case when count(*) > 0 then 1 else 0 end as hasRecharge from gl_recharge where user_id = #{userId} and status = 1")
    Boolean hasRecharge(@Param("userId") Integer userId);

    GlRecharge findLastRecharge(@Param("userId") Integer userId);

    GlRecharge selectForUpdate(@Param("orderId") String orderId);

    /**
     * 判断是否首存
     *
     * @param userId
     * @return
     */
    boolean isFirst(@Param("userId") Integer userId);

    boolean isFirstForFix(@Param("userId") Integer userId, @Param("startDate") Date startDate);

    /**
     * 判断是否虚拟币成功充值
     *
     * @param userId
     * @return
     */
    boolean hasDigitalPaySuccess(@Param("userId") Integer userId);

    List<GlRecharge> findRechargeList(@Param("startTime") Date startTime, @Param("endTime") Date endTime,
                                      @Param("merchant") Integer merchant, @Param("merchantCode") String merchantCode,
                                      @Param("channelId") Integer channelId,@Param("coin") String coin);

    List<GlRecharge> findRechargePendingPageList(RechargePendingQueryDto queryDto);

    List<RechargeVO> findRechargeApprovePageList(RechargeApproveQueryDto queryDto);

    List<GlRecharge> findRechargePageList(RechargeQueryDto queryDto);

    Integer updateForRequest(@Param("recharge") GlRecharge recharge);

    List<GlRechargeCollect> findRechargeRecordAmount(RechargeQueryDto queryDto);

    List<GlRechargeCollect> findRechargeRecordPayAmount(RechargeQueryDto queryDto);

    List<GlRechargeCollect> memberRechargeTotal(RechargeQueryDto queryDto);

    @Select("select sum(amount) from gl_recharge where user_id=#{userId} and create_date>=#{startTime} and create_date<=#{endTime} and status=1")
    BigDecimal getRechargeTotal(@Param("userId") Integer userId, @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    BigDecimal sumAmountByUserId(@Param("userId") Integer userId, @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    @Select("select payment_id AS paymentId, count(order_id) AS count from gl_recharge where last_update >= #{dayStart} and last_update <= #{dayEnd} and user_id = #{userId} and status = 1 group by payment_id")
    List<RechargeCountVO> countGroupByPaymentId(@Param("userId") Integer userId, @Param("dayStart") Date dayStart, @Param("dayEnd") Date dayEnd);

    @Select("SELECT MIN(create_date) FROM gl_recharge")
    Date selectFirstRechargeDate();

    List<GlRecharge> selectFixData(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("page") Integer page, @Param("size") Integer size);

    List<GlRecharge> selectRechargeActivity(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("paymentId") Integer paymentId, @Param("page") Integer page, @Param("size") Integer size);

    Integer cleanRechargeData(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("status") Integer status);

    GlRecharge findUserFirstRecharge(@Param("userId") Integer userId);

    @Select("select sum(amount) from gl_recharge where create_date>=#{startTime} and create_date<=#{endTime} and status=#{status}")
    BigDecimal getRechargeAmount(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("status") Integer status);

    @Select("select order_id from gl_recharge where create_date>=#{startTime} and create_date<=#{endTime} and status=#{status} limit 5000")
    List<String> getRechargeOrders(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("status") Integer status);

    /**
     * 查询对应的充值笔数
     * @param paymentId 充值方式id
     * @param merchantId 商户id
     * @param amount 充值金额
     * @return
     */
    Integer queryPendingCount(@Param("paymentId") Integer paymentId, @Param("merchantId") Integer merchantId, @Param("amount") BigDecimal amount);

    List<GlRecharge> findForRebate(@Param("startTime") Date startTime, @Param("endTime") Date endTime);


    Integer queryCount(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("merchantId") Integer merchantId, @Param("paymentId") Integer paymentId, @Param("status") Integer status);

    List<RechargeCountVO> queryMerchantCount(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("merchantIds") List<Integer> merchantIds, @Param("paymentId") Integer paymentId, @Param("status") Integer status);
}