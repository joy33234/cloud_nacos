package com.seektop.fund.controller.forehead.param.withdraw;

import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.digital.model.DigitalCoin;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawSubmitDO extends ParamBaseDO implements Serializable {

    private static final long serialVersionUID = 9208487671425092070L;


    /**
     * 提现卡ID
     */
    private Integer cardId;

    /**
     * USDT地址ID
     */
    private Integer usdtId;

    /**
     * 提现金额
     */
    @NotNull(message = "amount 参数不能为空")
    private BigDecimal amount;

    /**
     * 短信验证码
     */
    private String code;

    /**
     * 提现类型（1-普通提现、2-快速提现、3-代理提现、4-极速提现）
     */
    private Integer type = 1;


    private BigDecimal arrivalAmount;

    /**
     * 币种
     */
    private String coin;


}
