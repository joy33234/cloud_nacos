package com.seektop.fund.service;


import com.seektop.common.rest.rpc.RPCResponse;

import java.math.BigDecimal;
import java.util.Date;

public interface ProxyCreditPayoutLogService {

    RPCResponse<BigDecimal> sumAllPayoutAmountByProxyId(Date startDate, Date endDate, Integer proxyId);

}
