package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.vo.*;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 提现订单记录
 */
public interface GlWithdrawMapper extends Mapper<GlWithdraw> {

    GlWithdraw selectForUpdate(@Param("orderId") String orderId);

    /**
     * 更新提现记录
     *
     * @param withdraw
     * @return
     */
    Integer updateWithdraw(@Param("withdraw") GlWithdraw withdraw);

    List<GlWithdraw> selectByOrderIds(List<String> orderId);

    /**
     * 查询用户提现总额
     *
     * @param param
     * @return
     */
    //TODO Map To Dto
    BigDecimal getWithdrawAmountTotal(Map<String, Object> param);

    /**
     * 查询用户当前时间是否有 首提金额过大风险记录
     *
     * @param userId
     * @param createDate
     * @return
     */
    int selectCountByFirstAmountRisk(@Param("userId") Integer userId, @Param("createDate") Date createDate);

    /**
     * 获取用户最后提现记录
     *
     * @param userId
     * @return
     */
    GlWithdraw getLastWithdraw(@Param("userId") Integer userId);

    List<GlWithdrawDetailDto> getWithdrawList(@Param("startTime") Date startTime, @Param("endTime") Date endTime,
                                              @Param("merchantCode") String merchantCode, @Param("channelId") Integer channelId, @Param("coin") String coin);

    List<WithdrawVO> getWithdrawByPage(GlWithdrawQueryDto queryDto);

    /**
     * 提现分单分页查询接口
     *
     * @param queryDto
     * @return
     */
    List<GlWithdraw> getWithdrawSeparatePageList(GlWithdrawQueryDto queryDto);

    List<GlWithdraw> getTransferRecord(WithdrawRecordListQueryDO queryDto);

    List<GlWithdrawAllCollect> getTransferRecordSum(WithdrawRecordListQueryDO queryDto);

    /**
     * 查询正常提现记录列表
     *
     * @param queryDto
     * @return
     */
    List<GlWithdraw> findWithdrawList(GlWithdrawQueryDto queryDto);

    void updateRemark(@Param("orderId") String orderId, @Param("remark") String remark);

    List<GlWithdrawAllCollect> getWithdrawCollect(GlWithdrawQueryDto queryDto);

    GlWithdraw getUserLastWithdrawInfo(GlWithdrawQueryDto queryDto);

    @Select("select count(*) from gl_withdraw where user_id = #{userId} and create_date >= #{createDate} and status in (1,8) ")
    Integer countSuccessWithdrawByUser(@Param("userId") Integer userId, @Param("createDate") Date createDate);

    @Select("select count(*) from gl_withdraw where user_id = #{userId} and coin = #{coin} and create_date >= #{createDate} and status in (1,8) ")
    Integer countSuccessWithdrawByUser2(@Param("userId") Integer userId, @Param("createDate") Date createDate, @Param("coin") String coin);

    Integer sumNormalWithdrawCount(@Param("userId") Integer userId, @Param("startTime") Date startTime, @Param("endTime") Date endTime,@Param("coin") String coin);

    GlWithdrawAllCollect memberWithdrawTotal(GlWithdrawQueryDto queryDto);

    List<WithdrawVO> findWithdrawExceptionList(WithdrawExceptionQueryDto exceptionDto);

    List<GlWithdraw> findWithdrawByOrderId(List<String> orderId);

    @Select("select max(create_date) from gl_withdraw where user_id = #{userId}")
    Date maxCreateDateWithdrawByUser(@Param("userId") Integer userId);

    @Select("select DISTINCT user_id from gl_withdraw where create_date BETWEEN #{minTime} AND #{maxTime} and amount BETWEEN #{minAmount} AND #{maxAmount} and status > -3 limit ${size}")
    List<Integer> getFinishExceptionUserId(@Param("minTime") Date minTime, @Param("maxTime") Date maxTime, @Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount, @Param("size") int size);

    @Select("select count(*) from gl_withdraw where create_date >= #{createDate} and status = -3 ")
    Integer approveCount( @Param("createDate") Date createDate);

    @Select("select case when sum(amount) is null then 0 else sum(amount) end as totalAmount from gl_withdraw where user_id = #{userId} and coin = #{coin} and status in (1,8,14) and create_date between #{startDate} and #{endDate}")
    BigDecimal getTotalAmount(@Param(value = "coin") String coin,
                              @Param(value = "userId") Integer userId,
                              @Param(value = "startDate") Date startDate,
                              @Param(value = "endDate") Date endDate);

}