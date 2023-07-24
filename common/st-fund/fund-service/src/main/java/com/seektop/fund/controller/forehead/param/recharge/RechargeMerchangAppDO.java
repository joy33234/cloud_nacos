package com.seektop.fund.controller.forehead.param.recharge;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RechargeMerchangAppDO {

    /**
     * 充值商户应用ID
     */
    private Integer merchantAppId;

    /**
     * 充值方式ID
     */
    private Integer paymentId;

    /**
     * 充值类型(0-普通充值、1-大额充值)
     */
    private Integer limitType;


    private Integer headerOsType;

    /**
     * 用户层级ID
     */
    private Integer levelId;

    /**
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 是否是内部支付
     */
    private Boolean innerPay;

    /**
     * vip等级
     */
    private Integer vipLevel;

    /**
     * 用户id
     */
    private Integer userId;

    /**
     * 付款人姓名
     */
    private String name;


    /**
     * 币种
     */
    private String coin;


}
