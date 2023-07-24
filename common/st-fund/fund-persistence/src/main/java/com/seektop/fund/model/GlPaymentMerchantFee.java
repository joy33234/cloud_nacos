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
@Table(name = "gl_payment_merchant_fee")
public class GlPaymentMerchantFee implements Serializable {
    /**
     * 主键ID
     */
    @Id
    @Column(name = "fee_id")
    private Integer feeId;

    /**
     * 额度分类（0-普通充值，1-大额充值）
     */
    @Column(name = "limit_type")
    private Integer limitType;

    /**
     * 支付渠道ID
     */
    @Column(name = "channel_id")
    private Integer channelId;

    /**
     * 渠道名称
     */
    @Column(name = "channel_name")
    private String channelName;

    /**
     * 三方商户ID
     */
    @Column(name = "merchant_id")
    private Integer merchantId;

    /**
     * 三方商户号
     */
    @Column(name = "merchant_code")
    private String merchantCode;

    /**
     * 支付方式ID
     */
    @Column(name = "payment_id")
    private Integer paymentId;

    /**
     * 支付方式名称
     */
    @Column(name = "payment_name")
    private String paymentName;

    /**
     * 手续费比例
     */
    @Column(name = "fee_rate")
    private BigDecimal feeRate;

    /**
     * 最大手续费金额
     */
    @Column(name = "max_fee")
    private BigDecimal maxFee;

    /**
     * 最低充值金额
     */
    @Column(name = "min_amount")
    private BigDecimal minAmount;

    /**
     * 最高充值金额
     */
    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    @Column(name = "create_date")
    private Date createDate;

    private String creator;

    @Column(name = "last_update")
    private Date lastUpdate;

    @Column(name = "last_operator")
    private String lastOperator;

}