package com.seektop.fund.payment;

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
public class RechargePrepareDO implements Serializable {

    private static final long serialVersionUID = 2267335689028841592L;

    /**
     * 订单ID
     */
    private String orderId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 附言
     */
    private String keyword;

    /**
     * 银行ID：0其他
     */
    private Integer bankId;

    /**
     * 银行名称
     */
    private String bankName;

    /**
     * 客户端类型：0PC，1H5，2安卓，3IOS，4PAD
     */
    private Integer clientType;

    /**
     * IP地址
     */
    private String ip;

    /**
     * 用户层级名称
     */
    private String userLevel;

    /**
     * 付款卡号
     */
    private String fromCardNo;

    /**
     * 付款人姓名
     */
    private String fromCardUserName;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 聚合支付，后台创建订单的原始订单
     */
    private String originalOrderId;

    /**
     * 区块协议
     */
    private String protocol;

    /**
     * 支付方式ID（牛币支付）
     */
    private Integer paymentTypeId;
}
