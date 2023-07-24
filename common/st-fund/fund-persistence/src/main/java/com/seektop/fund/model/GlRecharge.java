package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_recharge")
public class GlRecharge implements Serializable {

    private static final long serialVersionUID = -8779813714338475331L;

    /**
     * 订单ID
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 用户类型：0会员，1代理
     */
    @Column(name = "user_type")
    private Integer userType;

    /**
     * 用户名
     */
    private String username;

    /**
     * 币种代码
     */
    @Column(name = "coin")
    private String coin;

    /**
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 手续费
     */
    private BigDecimal fee;


    /**
     * 1、附言 - 2、ST.USDT支付 保存付款人地址
     */
    private String keyword;

    /**
     * 充值方式ID
     */
    @Column(name = "payment_id")
    private Integer paymentId;

    /**
     * 收款渠道ID
     */
    @Column(name = "channel_id")
    private Integer channelId;

    /**
     * 收款渠道名称
     */
    @Column(name = "channel_name")
    private String channelName;

    /**
     * 收款商户ID
     * GlPaymentMerchantaccount
     */
    @Column(name = "merchant_id")
    private Integer merchantId;

    /**
     * 三方商户号
     */
    @Column(name = "merchant_code")
    private String merchantCode;

    /**
     * 三方商户名称
     */
    @Column(name = "merchant_name")
    private String merchantName;

    /**
     * 银行ID：0其他
     */
    @Column(name = "bank_id")
    private Integer bankId;

    /**
     * 银行名称
     */
    @Column(name = "bank_name")
    private String bankName;

    /**
     * 客户端类型：0PC，1H5，2安卓，3IOS，4PAD
     */
    @Column(name = "client_type")
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
     * 1：支付成功，2：补单审核成功，3：补单审核拒绝，4：人工拒绝补单，5：用户撤销，6：超时撤销 7：超时未确认
     * FundConstant.RechargeSubStatus
     */
    @Column(name = "sub_status")
    private Integer subStatus;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 最后修改时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;

    /**
     * APP类型：0现金网、1体育、2代理
     */
    @Column(name = "app_type")
    private Integer appType;

    /**
     * 用户层级ID
     */
    @Column(name = "user_level")
    private String userLevel;

    /**
     * 额外的备注信息，目前用于充值订单被直接拒绝补单的情况
     */
    private String remark;

    /**
     * 充值额度类型，0：普通充值，1：大额充值
     */
    @Column(name = "limit_type")
    private Integer limitType;

    /**
     * 收款银行卡号
     */
    @Column(name = "card_no")
    private String cardNo;

    /**
     * 收款人姓名
     */
    @Column(name = "card_username")
    private String cardUsername;

    /**
     * 1代客充值  0普通充值  2:极速转卡
     */
    @Column(name = "agent_type")
    private Integer agentType;

    /**
     * 交易Hash
     */
    @Column(name = "tx_hash")
    private String txHash;

}