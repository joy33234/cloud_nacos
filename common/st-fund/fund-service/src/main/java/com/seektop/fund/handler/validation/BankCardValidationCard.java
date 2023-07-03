package com.seektop.fund.handler.validation;

import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import lombok.AllArgsConstructor;

import java.util.List;


@AllArgsConstructor
public class BankCardValidationCard implements DataValidation {

    private GlUserDO user;
    private List<GlWithdrawUserBankCard> cardList;
    @Override
    public void valid() throws GlobalException {
        if (UserConstant.Type.PROXY == user.getUserType()) {
            if (null != cardList && cardList.size() >= 10) {
                throw new GlobalException("最多绑定10张银行卡，请删除多余的银行卡后再次进行绑卡");
            }
        }
        else {
            if (null != cardList && cardList.size() >= 3) {
                throw new GlobalException("最多绑定3张银行卡，请删除多余的银行卡后再次进行绑卡");
            }
        }
    }
}
