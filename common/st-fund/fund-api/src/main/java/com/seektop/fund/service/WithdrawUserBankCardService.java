package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.bankCard.DeleteBankCardDto;
import com.seektop.fund.dto.param.bankCard.ResetBankCardDto;
import com.seektop.fund.dto.result.bankCard.GlWithdrawUserBankCardDO;
import com.seektop.fund.dto.result.bankCard.WithdrawUserBankCardPO;

import java.util.List;

public interface WithdrawUserBankCardService {

    RPCResponse<List<GlWithdrawUserBankCardDO>> findUserCards(Integer userId);
    /**
     * 用户使用中的银行卡
     * @param userId
     * @return
     */
    RPCResponse<List<WithdrawUserBankCardPO>> findUserActiveCardList(Integer userId);

    /**
     * 重置用户银行卡
     * @param resetBankCardDto
     * @return
     */
    RPCResponse<Boolean> resetBankCard(ResetBankCardDto resetBankCardDto) throws GlobalException;

    RPCResponse<Boolean> deleteBankCard(DeleteBankCardDto deleteBankCardDto) throws GlobalException;
}
