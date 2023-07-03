package com.seektop.fund.handler.validation;

import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.business.withdraw.WithdrawUserBankCardApplyBusiness;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.model.WithdrawUserBankCardApply;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Slf4j
@AllArgsConstructor
public class BankCardValidationCardExist implements DataValidation {

    private String cardNo;
    private GlWithdrawUserBankCardBusiness cardBusiness;
    private WithdrawUserBankCardApplyBusiness cardApplyBusiness;


    @Override
    public void valid() throws GlobalException {
        // 检查是否已存在绑卡
        GlWithdrawUserBankCard card = cardBusiness.findByCardNo(cardNo);
        if(!ObjectUtils.isEmpty(card)){
            log.error("银行卡已绑定，cardNo:{}", cardNo);
            throw new GlobalException(ResultCode.CARD_EXIST_ERROR);
        }
        // 检查是否已存在人工绑卡申请
        List<WithdrawUserBankCardApply> list = cardApplyBusiness.findByCardNo(cardNo);
        if(!CollectionUtils.isEmpty(list)){
            log.error("银行卡人工绑卡申请审核中，cardNo:{}", cardNo);
            throw new GlobalException(ResultCode.CARD_EXIST_ERROR);
        }
    }
}
