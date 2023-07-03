package com.seektop.fund.controller.backend.dto;

import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.dto.GlUserDO;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import lombok.Data;

import java.util.List;

@Data
public class BankCardOperatingDto extends ParamBaseDO {

    private Integer operationType;
    private GlWithdrawUserBankCard bankCard;
    private List<GlWithdrawUserBankCard> cardList;
    private GlUserDO user;

    /**
     * 是否用户操作
     */
    private Boolean userOperating = true;
}
