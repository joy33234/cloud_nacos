package com.seektop.fund.service.impl;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponse.Builder;
import com.seektop.constant.ProjectConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBankBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.business.withdraw.WithdrawUserBankCardApplyBusiness;
import com.seektop.fund.dto.param.bankCard.BankCardApplyDto;
import com.seektop.fund.model.GlWithdrawBank;
import com.seektop.fund.service.WithdrawUserBankCardApplyService;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;

@Service(timeout = 3000, interfaceClass = WithdrawUserBankCardApplyService.class)
public class WithdrawUserBankCardApplyServiceImpl implements WithdrawUserBankCardApplyService {

    @Autowired
    private WithdrawUserBankCardApplyBusiness userBankCardApplyBusiness;
    @Autowired
    private GlWithdrawUserBankCardBusiness userBankCardBusiness;
    @Autowired
    private GlWithdrawBankBusiness bankBusiness;

    @Override
    public RPCResponse<Boolean> firstApprove(BankCardApplyDto applyDto) throws GlobalException {
        GlWithdrawBank bank = bankBusiness.findById(applyDto.getBankId());
        if(ProjectConstant.UserManageStatus.FIRST_SUCCESS == applyDto.getStatus() && ObjectUtils.isEmpty(bank)) {
            throw new GlobalException("不支持的银行");
        }
        userBankCardApplyBusiness.firstApprove(applyDto, bank);
        Builder<Boolean> newBuilder = RPCResponse.newBuilder();
        return newBuilder.success().setData(true).build();
    }

    @Override
    public RPCResponse<Boolean> secondApprove(BankCardApplyDto applyDto) throws GlobalException {
        userBankCardBusiness.secondApprove(applyDto);
        Builder<Boolean> newBuilder = RPCResponse.newBuilder();
        return newBuilder.success().setData(true).build();
    }
}
