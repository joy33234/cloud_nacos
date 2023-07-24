package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.recharge.RebateAwardDto;
import com.seektop.fund.dto.param.recharge.RechargeTotalParamDO;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.dto.result.recharge.RechargeCountVo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public interface GlRechargeService {

    /**
     * 充值订单超时撤销
     *
     * @param recharge
     * @throws GlobalException
     */
    void doRechargeFailed(GlRechargeDO recharge) throws GlobalException;

    /**
     * C2C充值订单超时撤销
     *
     * @throws GlobalException
     */
    public void doC2CRechargeFailed() throws GlobalException;

    /**
     * 获取超时充值订单
     *
     * @return
     * @throws GlobalException
     */
    RPCResponse<List<GlRechargeDO>> findExpiredList(int minutes, Integer subStatus) throws GlobalException;

    /**
     * 获取用户指定时间段内的充值金额
     *
     * @param paramDO
     * @return
     */
    RPCResponse<BigDecimal> getRechargeTotal(RechargeTotalParamDO paramDO);

    /**
     * 获取用户充值金额
     *
     * @param userId
     * @return
     */
    RPCResponse<BigDecimal> sumAmountByUserId(Integer userId);


    /**
     * 根据支付方式分组统计一段时间内的支付方式使用次数
     *
     * @param userId
     * @param dayStart
     * @param dayEnd
     * @return
     */
    RPCResponse<List<RechargeCountVo>> countGroupByPaymentId(Integer userId, Date dayStart, Date dayEnd);

    /**
     * 查询充值记录最早一条记录
     *
     * @return
     */
    RPCResponse<Date> selectFirstRechargeDate();

    void firstRechargeReport(GlRechargeDO rechargeDO);

    RPCResponse<List<GlRechargeDO>> selectFixData(Date fixDate, Integer page, Integer size);

    RPCResponse<List<GlRechargeDO>> selectRechargeData(Date startDate, Date endDate, Integer paymentId, Integer page, Integer size);

    /**
     * 清除一段时间内的充值记录
     *
     * @param startDate
     * @param endDate
     * @param status
     */
    void cleanRechargeData(Date startDate, Date endDate, Integer status);

    void rechargeDataReport(String orderId);

    void firstRechargeDataReport(GlUserDO userDO);

    RPCResponse<Integer> fixEsData(long stime, long etime);

    RPCResponse<Integer> reportEsByChannelIdData(long stime, long etime, int channel);

    /**
     * 查询数据补发充值返利
     * @param rebateAwardDto
     */
    void rechargeRebateAward(RebateAwardDto rebateAwardDto);


    /**
     * 获取充值列表
     * @param orderIds
     * @return
     */
    RPCResponse<List<GlRechargeDO>> findByOrderIds(List<String> orderIds);


}