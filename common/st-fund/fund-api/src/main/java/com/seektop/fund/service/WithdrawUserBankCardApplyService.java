package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.bankCard.BankCardApplyDto;

public interface WithdrawUserBankCardApplyService {

    /**
     * 绑卡申请一审处理
     * @param applyDto
     * @return
     * @throws GlobalException
     */
    RPCResponse<Boolean> firstApprove(BankCardApplyDto applyDto) throws GlobalException;

    /**
     * 绑卡申请二审处理
     * @param applyDto
     * @return
     * @throws GlobalException
     */
    RPCResponse<Boolean> secondApprove(BankCardApplyDto applyDto) throws GlobalException;
}
