package com.seektop.fund.controller.forehead.param.recharge;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RechargeSubmitDO extends ParamBaseDO {


    /**
     * 充值商户应用ID
     */
    @NotNull(message = "参数异常:merchantAppId Not Null")
    private Integer merchantAppId;

    /**
     * 充值方式ID
     */
    @NotNull(message = "参数异常:paymentId Not Null")
    private Integer paymentId;

    /**
     * 充值金额
     */
    @DecimalMin(value = "100", message = "amount minValue 100")
    private BigDecimal amount;

    /**
     * 充值类型(0-普通充值、1-大额充值)
     */
    @NotNull(message = "参数异常:limitType Not Null")
    private Integer limitType;

    /**
     * 银行ID，网银支付时需要
     */
    @Min(value = 1, message = "参数异常:bankId minValue 1")
    @Max(value = 15, message = "参数异常:bankId maxValue 15")
    private Integer bankId;

    /**
     * 币种 : 0数字货币，1人民币，
     */
    @NotNull(message = "参数异常:coin Not Null")
    private String coin;

    /**
     * 银行卡号，快捷支付时需要
     */
    private String cardNo;

    /**
     * 存款姓名
     */
    private String name;

    /**
     * 时间戳
     */
    @NotNull(message = "参数异常:timestamp Not Null")
    private long timestamp;

    /**
     * 区块协议
     */
    private String protocol;

    /**
     * 待客充值 用户名
     */
    private String userName;

    /**
     * 待客充值类型
     */
    private Integer agencyId;

    /**
     * 支付类型
     */
    private Integer payType = 0;

    /**
     * 代客充值 使用 管理员 token
     */
    private String token;

    /**
     * 牛币支付类型ID
     */
    private Integer paymentTypeId;
}
