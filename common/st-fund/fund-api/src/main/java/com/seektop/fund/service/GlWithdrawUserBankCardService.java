package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.fund.dto.result.bankCard.GlWithdrawUserBankCardDO;

import java.util.List;

public interface GlWithdrawUserBankCardService {
    RPCResponse<List<GlWithdrawUserBankCardDO>> findUserCards(Integer userId);

    RPCResponse<GlWithdrawUserBankCardDO> findCardId(Integer cardId);
}
