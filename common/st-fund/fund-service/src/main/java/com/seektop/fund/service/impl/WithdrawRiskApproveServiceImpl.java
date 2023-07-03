package com.seektop.fund.service.impl;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.fund.business.withdraw.WithdrawRiskApproveBusiness;
import com.seektop.fund.dto.param.withdraw.RiskApproveDO;
import com.seektop.fund.model.GlWithdrawRiskApprove;
import com.seektop.fund.service.WithdrawRiskApproveService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

@DubboService(timeout = 5000, interfaceClass = WithdrawRiskApproveService.class)
public class WithdrawRiskApproveServiceImpl implements WithdrawRiskApproveService {

    @Autowired
    private WithdrawRiskApproveBusiness riskApproveBusiness;

    @Override
    public RPCResponse<List<RiskApproveDO>> findByOrderIds(List<String> orderIds) {
        List<GlWithdrawRiskApprove> list = riskApproveBusiness.findByOrderIds(orderIds);
        List<RiskApproveDO> riskApproveDOList = list.stream().map(a -> {
            RiskApproveDO riskApproveDO = new RiskApproveDO();
            BeanUtils.copyProperties(a, riskApproveDO);
            return riskApproveDO;
        }).collect(Collectors.toList());
        return RPCResponseUtils.buildSuccessRpcResponse(riskApproveDOList);
    }
}
