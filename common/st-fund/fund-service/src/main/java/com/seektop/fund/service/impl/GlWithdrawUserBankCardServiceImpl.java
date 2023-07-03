package com.seektop.fund.service.impl;

import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.dto.result.bankCard.GlWithdrawUserBankCardDO;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.service.GlWithdrawUserBankCardService;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service(timeout = 5000, interfaceClass = GlWithdrawUserBankCardService.class, validation ="true")
public class GlWithdrawUserBankCardServiceImpl implements GlWithdrawUserBankCardService {
    @Resource
    private GlWithdrawUserBankCardBusiness glWithdrawUserBankCardBusiness;

    @Override
    public RPCResponse<List<GlWithdrawUserBankCardDO>> findUserCards(Integer userId) {
        RPCResponse.Builder<List<GlWithdrawUserBankCardDO>> newBuilder = RPCResponse.newBuilder();
        List<GlWithdrawUserBankCard> result =  glWithdrawUserBankCardBusiness.findUserActiveCardList(userId);
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
    public RPCResponse<GlWithdrawUserBankCardDO> findCardId(Integer cardId) {
        return RPCResponseUtils.buildSuccessRpcResponse(DtoUtils.transformBean(glWithdrawUserBankCardBusiness.findById(cardId), GlWithdrawUserBankCardDO.class));
    }
}
