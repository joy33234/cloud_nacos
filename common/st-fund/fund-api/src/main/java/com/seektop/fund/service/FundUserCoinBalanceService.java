package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.fund.dto.param.account.UserCoinAccountChangeDO;
import com.seektop.fund.dto.result.account.FundUserCoinAccountDO;
import com.seektop.report.fund.HandlerResponse;


public interface FundUserCoinBalanceService {

    /**
     * 获取玩家金币额度
     * @param userId 玩家id
     * @return
     */
    RPCResponse<FundUserCoinAccountDO> getFundUserCoinBalance(Integer userId);

    /**
     * 玩家金币额度加减币
     * @return
     */
    HandlerResponse fundUserCoinAccountChange(UserCoinAccountChangeDO userCoinAccountChangeDO);


}