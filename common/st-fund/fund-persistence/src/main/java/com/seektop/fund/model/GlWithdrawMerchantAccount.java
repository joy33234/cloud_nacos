package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 出款商户设置
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"script"})
@Table(name = "gl_withdraw_merchantaccount")
public class GlWithdrawMerchantAccount implements Serializable {
    /**
     * 账号ID
     */
    @Id
    @Column(name = "merchant_id")
    private Integer merchantId;

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
     * 商户号
     */
    @Column(name = "merchant_code")
    private String merchantCode;

    /**
     * 支付地址
     */
    @Column(name = "pay_url")
    private String payUrl;

    /**
     * 通知地址
     */
    @Column(name = "notify_url")
    private String notifyUrl;

    /**
     * 公钥
     */
    @Column(name = "public_key")
    private String publicKey;

    /**
     * 私钥
     */
    @Column(name = "private_key")
    private String privateKey;


    @Column(name = "merchant_fee_type")
    private Integer merchantFeeType;


    @Column(name = "merchant_fee")
    private BigDecimal merchantFee;

    /**
     * 每日出款上限
     */
    @Column(name = "daily_limit")
    private Integer dailyLimit;

    /**
     * 出款最小金额
     */
    @Column(name = "min_amount")
    private BigDecimal minAmount;

    /**
     * 出款最大金额
     */
    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 商户状态：0上架，1下架，2已删除
     */
    private Integer status;

    /**
     * 开启状态： 0 已开启、 1 已关闭
     */
    @Column(name = "open_status")
    private Integer openStatus;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 最后修改人
     */
    @Column(name = "last_operator")
    private String lastOperator;

    /**
     * 最后修改时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;

    /**
     * 支付代付的银行id
     */
    @Column(name = "bank_id")
    private String bankId;

    /**
     * 支付姓名类型
     */
    @Column(name = "name_type")
    private String nameType;

    @Transient
    private String displayName;

    /**
     * 今日已出款金额
     */
    @Transient
    private BigDecimal successAmount;


    /**
     * 是否启用动态脚本 0：不启用（默认），1：启用
     */
    @Column(name = "enable_script")
    private Integer enableScript;

    @Column(name = "script")
    private String script;

    @Column(name = "script_sign")
    private String scriptSign;

}