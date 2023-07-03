package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.fund.dto.param.withdraw.RiskApproveDO;

import java.util.List;

public interface WithdrawRiskApproveService {

    /**
     * 获取提款审核结果
     * @param orderIds
     * @return
     */
    RPCResponse<List<RiskApproveDO>> findByOrderIds(List<String> orderIds);
}
