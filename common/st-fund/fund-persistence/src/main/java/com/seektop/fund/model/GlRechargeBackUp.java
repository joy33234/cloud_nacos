package com.seektop.fund.model;

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
public class GlRechargeBackUp implements Serializable {


    private static final long serialVersionUID = 158546420917161527L;

    /**
     * 订单ID
     */
    private String orderId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 用户类型：0会员，1代理
     */
    private Integer userType;

    /**
     * 用户名
     */
    private String username;

    /**
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 手续费
     */
    private BigDecimal fee;


    /**
     * 附言
     */
    private String keyword;

    /**
     * 充值方式ID
     */
    private Integer paymentId;

    /**
     * 收款渠道ID
     */
    private Integer channelId;

    /**
     * 收款渠道名称
     */
    private String channelName;

    /**
     * 收款商户ID
     */
    private Integer merchantId;

    /**
     * 三方商户号
     */
    private String merchantCode;

    /**
     * 三方商户名称
     */
    private String merchantName;

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
     * 支付状态：0：待支付，1：支付成功，2：支付失败，3：补单审核中
     * FundConstant.RechargeStatus
     */
    private Integer status;

    /**
     * 支付子状态，用于对状态的补充，根据场景细分列表的展示
     * 1：支付成功，2：补单审核成功，3：补单审核拒绝，4：人工拒绝补单，5：用户撤销，6：超时撤销
     * FundConstant.RechargeSubStatus
     */
    private Integer subStatus;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 最后修改时间
     */
    private Date lastUpdate;

    /**
     * APP类型：0现金网、1体育、2代理
     */
    private Integer appType;

    /**
     * 用户层级ID
     */
    private String userLevel;

    /**
     * 额外的备注信息，目前用于充值订单被直接拒绝补单的情况
     */
    private String remark;

    /**
     * 充值额度类型，0：普通充值，1：大额充值
     */
    private Integer limitType;

    /**
     * 收款银行卡号
     */
    private String cardNo;

    /**
     * 收款人姓名
     */
    private String cardUsername;

    /**
     * 1代客充值  0普通充值
     */
    private Integer agentType;
}