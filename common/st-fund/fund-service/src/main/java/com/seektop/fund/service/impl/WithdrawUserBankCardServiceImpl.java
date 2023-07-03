package com.seektop.fund.service.impl;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponse.Builder;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.dto.param.bankCard.DeleteBankCardDto;
import com.seektop.fund.dto.param.bankCard.ResetBankCardDto;
import com.seektop.fund.dto.result.bankCard.GlWithdrawUserBankCardDO;
import com.seektop.fund.dto.result.bankCard.WithdrawUserBankCardPO;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.service.WithdrawUserBankCardService;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service(timeout = 3000, interfaceClass = WithdrawUserBankCardService.class)
public class WithdrawUserBankCardServiceImpl implements WithdrawUserBankCardService {

    @Autowired
    private GlWithdrawUserBankCardBusiness userBankCardBusiness;

    @Override
    public RPCResponse<List<GlWithdrawUserBankCardDO>> findUserCards(Integer userId) {
        RPCResponse.Builder<List<GlWithdrawUserBankCardDO>> newBuilder = RPCResponse.newBuilder();
        List<GlWithdrawUserBankCard> result =  userBankCardBusiness.findUserActiveCardList(userId);
        if(result == null){
            return newBuilder.success().setData(null).build();
        }

        List<GlWithdrawUserBankCardDO> voresult = result.stream().map(x -> {
            GlWithdrawUserBankCardDO vo = new GlWithdrawUserBankCardDO();
            BeanUtils.copyProperties(x, vo);
            return vo;
        }).collect(Collectors.toList());
        return newBuilder.success().setData(voresult).build();
    }

    @Override
    public RPCResponse<List<WithdrawUserBankCardPO>> findUserActiveCardList(Integer userId) {
        Builder<List<WithdrawUserBankCardPO>> newBuilder = RPCResponse.newBuilder();
        List<GlWithdrawUserBankCard> cards = userBankCardBusiness.findUserActiveCardList(userId);
        List<WithdrawUserBankCardPO> list;
        if(!CollectionUtils.isEmpty(cards)) {
            list = cards.stream().map(c -> {
                WithdrawUserBankCardPO card = new WithdrawUserBankCardPO();
                BeanUtils.copyProperties(c, card);
                return card;
            }).collect(Collectors.toList());
        }
        else {
            list = new ArrayList<>();
        }
        return newBuilder.success().setData(list).build();
    }

    @Override
    public RPCResponse<Boolean> resetBankCard(ResetBankCardDto resetBankCardDto) throws GlobalException {
        userBankCardBusiness.resetBankCard(resetBankCardDto);
        Builder<Boolean> newBuilder = RPCResponse.newBuilder();
        return newBuilder.success().setData(true).build();
    }

    @Override
    public RPCResponse<Boolean> deleteBankCard(DeleteBankCardDto deleteBankCardDto) throws GlobalException {
        userBankCardBusiness.deleteBankCard(deleteBankCardDto);
        Builder<Boolean> newBuilder = RPCResponse.newBuilder();
        return newBuilder.success().setData(true).build();
    }
}
