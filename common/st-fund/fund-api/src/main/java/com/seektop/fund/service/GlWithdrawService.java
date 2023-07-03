package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.withdraw.RiskApproveDto;
import com.seektop.fund.dto.result.withdraw.GlWithdrawDO;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface GlWithdrawService {

    RPCResponse<Integer> countSuccessWithdrawByUser(Integer userId, Date createDate,String coinCode);

    RPCResponse<Map<String, Integer>> sumWithdrawCount(Integer userId, Date sTime, Date eTime,String coinCode);

    RPCResponse<BigDecimal> sumWithdrawingTotal(Integer userId);

    RPCResponse<List<GlWithdrawDO>> findWithdrawReturnList();

    RPCResponse<Void> manualReturnWithdraw(GlWithdrawDO withdrawDO) throws GlobalException;

    RPCResponse<GlWithdrawDO> findWithdrawById(String orderId);

    RPCResponse<List<GlWithdrawDO>> findByOrderIds(List<String> orderIds);

    /**
     * 对-3风险待审核，-4 审核搁置状态的提款风险审核
     * @param riskApproveDto
     * @return
     */
    RPCResponse<Boolean> doWithdrawRiskApprove(RiskApproveDto riskApproveDto) throws GlobalException;

    /**
     * 查询正在提现的金额
     *
     * @param userId
     * @return
     */
    RPCResponse<BigDecimal> getWithdrawingTotal(Integer userId);

    /**
     * 提现数据修复
     *
     * @param startDate
     * @param endDate
     * @return
     */
    RPCResponse<Void> fixData(Date startDate, Date endDate);

    RPCResponse<List<String>> findOrderIds(Integer userId, Integer status, Date sTime, Date eTime);

    /**
     * 同步三方提现订单
     *
     * @param channelIds
     * @return
     */
    RPCResponse<Void> syncWithdraw(String channelIds) throws GlobalException;

    /**
     * 提现订单超时未撮合
     *
     * @return
     */
    RPCResponse<Boolean> unMatch() throws GlobalException;


    /**
     * 提现风控
     * @return
     */
    RPCResponse<List<Integer>> getRiskType(GlWithdrawDO withdrawDO, GlUserDO userDO, Date now);



}