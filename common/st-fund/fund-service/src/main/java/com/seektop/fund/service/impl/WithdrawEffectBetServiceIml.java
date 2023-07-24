package com.seektop.fund.service.impl;

import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.GlWithdrawEffectBetBusiness;
import com.seektop.fund.business.GlWithdrawUserUsdtAddressBusiness;
import com.seektop.fund.dto.param.withdraw.DeleteUsdtAddressDto;
import com.seektop.fund.dto.result.withdraw.GlWithdrawEffectBetDO;
import com.seektop.fund.dto.result.withdraw.WithdrawUserUsdtAddressDO;
import com.seektop.fund.model.GlWithdrawEffectBet;
import com.seektop.fund.model.GlWithdrawUserUsdtAddress;
import com.seektop.fund.service.WithdrawEffectBetService;
import com.seektop.fund.service.WithdrawUserUsdtAddressService;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

@DubboService(timeout = 5000, interfaceClass = WithdrawEffectBetService.class, validation = "true")
public class WithdrawEffectBetServiceIml implements WithdrawEffectBetService {

    @Resource
    private GlWithdrawEffectBetBusiness glWithdrawEffectBetBusiness;
    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    @Override
    public RPCResponse<GlWithdrawEffectBetDO> findOne(Integer userId,String coinCode) throws GlobalException {
        GlWithdrawEffectBet glWithdrawEffectBet = glWithdrawEffectBetBusiness.findOne(userId, coinCode);
        return RPCResponseUtils.buildSuccessRpcResponse(DtoUtils.transformBean(glWithdrawEffectBet, GlWithdrawEffectBetDO.class));
    }


    @Override
    public RPCResponse<Boolean> updateDigitalEffect(Integer userId, String tradeId, String coinCode, BigDecimal amount, Integer paymentId) throws GlobalException {
        Boolean result = glFundUserAccountBusiness.updateDigitalEffect(userId, tradeId,coinCode,amount,paymentId);
        return RPCResponseUtils.buildSuccessRpcResponse(result);
    }


}
