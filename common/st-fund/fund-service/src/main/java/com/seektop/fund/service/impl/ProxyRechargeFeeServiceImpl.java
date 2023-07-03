package com.seektop.fund.service.impl;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.fund.mapper.ProxyRechargeFeeMapper;
import com.seektop.fund.service.ProxyRechargeFeeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@DubboService(timeout = 3000, interfaceClass = ProxyRechargeFeeService.class)
public class ProxyRechargeFeeServiceImpl implements ProxyRechargeFeeService {

    @Resource
    private ProxyRechargeFeeMapper proxyRechargeFeeMapper;

    @Override
    public RPCResponse<BigDecimal> sumRebateByTime(Integer proxyId, String coinCode, Date startDate, Date endDate) {
        //todo 目前其他币种不支持代冲
        return RPCResponseUtils.buildSuccessRpcResponse(DigitalCoinEnum.CNY.getCode().equals(coinCode)?proxyRechargeFeeMapper.sumRebateByTime(proxyId,startDate,endDate):BigDecimal.ZERO);
    }
}
