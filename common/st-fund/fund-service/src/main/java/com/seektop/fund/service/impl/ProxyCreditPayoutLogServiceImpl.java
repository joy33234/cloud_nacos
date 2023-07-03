package com.seektop.fund.service.impl;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.fund.business.GlFundBusiness;
import com.seektop.fund.mapper.ProxyCreditPayoutLogMapper;
import com.seektop.fund.service.ProxyCreditPayoutLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Service(timeout = 3000, interfaceClass = ProxyCreditPayoutLogService.class)
public class ProxyCreditPayoutLogServiceImpl implements ProxyCreditPayoutLogService {

    @Resource
    private GlFundBusiness glFundBusiness;

    @Resource
    private ProxyCreditPayoutLogMapper proxyCreditPayoutLogMapper;

    @Override
    public RPCResponse<BigDecimal> sumAllPayoutAmountByProxyId(Date startDate, Date endDate, Integer proxyId) {
        BigDecimal amount = proxyCreditPayoutLogMapper.sumAllPayoutAmount(startDate, endDate, proxyId);
        return RPCResponseUtils.buildSuccessRpcResponse(amount == null ? BigDecimal.ZERO : amount);
    }
}
