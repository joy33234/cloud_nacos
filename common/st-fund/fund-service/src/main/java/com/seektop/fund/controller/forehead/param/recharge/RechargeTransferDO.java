package com.seektop.fund.controller.forehead.param.recharge;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RechargeTransferDO extends ParamBaseDO {

    @NotNull(message = "参数异常:merchantAppId Not Null")
    private Integer merchantAppId;

    @NotNull(message = "参数异常:amount Not Null")
    @DecimalMin(value = "100", message = "amount minValue 100")
    private BigDecimal amount;

    @NotNull(message = "参数异常:limitType Not Null")
    private Integer limitType;

    /**
     * 存款银行卡ID
     */
    private Integer bankId;

    /**
     * 存款人姓名
     */
    private String name;

    /**
     * 付款卡号
     */
    private String cardNo;

    /**
     * 区块协议
     */
    private String protocol;

    /**
     * 待客充值
     */
    private String userName;

    private Integer agencyId;

    private Integer appType;

    private Integer payType = 0;

    /**
     * 支付方式
     */
    private Integer paymentId;

    /**
     * 牛币支付类型ID
     */
    private Integer paymentTypeId;

    /**
     * 是否更换充值方式 （极速支付）
     */
    private Boolean changePayType = false;

    /**
     * 币种 : 0数字货币，1人民币，
     */
    @NotNull(message = "参数异常:coin Not Null")
    private String coin;

}
