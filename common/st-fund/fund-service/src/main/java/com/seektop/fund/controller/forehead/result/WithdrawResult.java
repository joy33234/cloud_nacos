package com.seektop.fund.controller.forehead.result;

import com.seektop.fund.model.GlWithdrawUserBankCard;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class WithdrawResult implements Serializable {

    private static final long serialVersionUID = -4338953808707425942L;

    private String telephone;

    //提现开关
    private String onOff;

    /**
     * 提现订单显示方式：BANK、DIGITAL
     */
    private String showType;

    private List<GlWithdrawUserBankCard> bankCards;

    private List<GlWithdrawDetailResult> withdraws;
}
