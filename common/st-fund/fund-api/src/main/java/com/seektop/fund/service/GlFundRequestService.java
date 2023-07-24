package com.seektop.fund.service;


import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.account.ReduceRequestDto;
import com.seektop.fund.dto.param.account.UserBalanceRequestDO;

import java.math.BigDecimal;
import java.util.Date;

public interface GlFundRequestService {

    /**
     * 虚拟会员初始化金额
     *
     * @param dto
     * @return
     * @throws GlobalException
     */
    RPCResponse<Void> adjustUserBalance(UserBalanceRequestDO dto) throws GlobalException;

    /**
     * 错误代充总额
     * @param startDate
     * @param endDate
     * @param userId
     * @return
     * @throws GlobalException
     */
    RPCResponse<BigDecimal> sumWrongRechargeTotal(Date startDate, Date endDate, Integer userId);

    /**
     * 转账异常减币处理申请
     * @param dto
     * @return
     */
    RPCResponse<Boolean> addReduceRequest(ReduceRequestDto dto) throws GlobalException;

}
