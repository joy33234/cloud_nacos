package com.seektop.fund.controller.backend.param.recharge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class RechargePayDO implements Serializable {


    private static final long serialVersionUID = 1092455625431113903L;

    /**
     * 充值订单号
     */
    private String orderId;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 手续费
     */
    private BigDecimal fee;

    /**
     * 支付时间
     */
    private Date payDate;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 第三方订单号
     */
    private String thirdOrderId;

    /**
     * 1-充值补单、0-回调充值
     */
    private Integer isApprove;

    /**
     * 0-待处理/1-成功
     */
    private Integer status;

    /**
     * 入款商户
     */
    private String channelName;

    /**
     * 充值姓名
     */
    private String name;

}
