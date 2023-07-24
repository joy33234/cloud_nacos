package com.seektop.fund.service;


import com.seektop.common.rest.rpc.RPCResponse;

import java.math.BigDecimal;
import java.util.Date;

public interface ProxyRechargeFeeService {

    RPCResponse<BigDecimal> sumRebateByTime(Integer userId, String coinCode, Date startTime, Date endTime);
}
