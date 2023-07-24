package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.withdraw.RiskApproveDO;
import com.seektop.fund.dto.result.withdraw.GlWithdrawEffectBetDO;

import java.math.BigDecimal;
import java.util.List;

public interface WithdrawEffectBetService {

    /**
     * 获取用户流水
     * @param userId
     * @param coinCode
     * @return
     */
    RPCResponse<GlWithdrawEffectBetDO> findOne(Integer userId, String coinCode) throws GlobalException;

    /**
     * 充值成功更新用户流水
     * @param userId
     * @param coinCode
     * @return
     */
    RPCResponse<Boolean> updateDigitalEffect(Integer userId, String tradeId, String coinCode, BigDecimal amount, Integer paymentId) throws GlobalException;


}
