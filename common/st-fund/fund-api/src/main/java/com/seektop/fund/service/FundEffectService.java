package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.effect.CleanWithdrawEffectDto;
import com.seektop.fund.dto.param.effect.EffectApproveDto;
import com.seektop.fund.dto.param.effect.EffectUpdateStatus;
import com.seektop.fund.dto.result.withdraw.GlWithdrawEffectBetDO;

import java.util.List;

/**
 * 提现流水相关服务
 */
public interface FundEffectService {
    /**
     * 流水清零二审
     * @param cleanWithdrawEffectDto
     * @throws GlobalException
     */
    void cleanWithdrawEffect(CleanWithdrawEffectDto cleanWithdrawEffectDto) throws GlobalException;

    /**
     * 清除目标订单流水 二审
     * @param dto
     * @throws GlobalException
     */
    void bettingBalanceRemove(EffectApproveDto dto) throws GlobalException;

    /**
     * 恢复目标订单流水 二审
     * @param effectApproveDto
     * @throws GlobalException
     */
    void bettingBalanceRecover(EffectApproveDto effectApproveDto) throws GlobalException;

    void adjustBetBalance(EffectApproveDto effectApproveDto) throws GlobalException;

    void updateRecordStatusByOrderId(EffectUpdateStatus effectUpdateStatus);

    /**
     * 获取用户流水和有效流水
     * @param userId
     * @return
     */
    RPCResponse<List<GlWithdrawEffectBetDO>> getUserWithdrawEffect(Integer userId);

    /**
     * 获取用户流水和有效流水
     * @param userId
     * @return
     */
    RPCResponse<GlWithdrawEffectBetDO> getUserWithdrawEffect(Integer userId,String coin);

}
