package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"script"})
@Table(name = "gl_payment_merchantaccount")
public class GlPaymentMerchantaccount {

    private static final long serialVersionUID = -5557633035051037848L;
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
     * 支付渠道名称
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
     * 结果跳转地址
     */
    @Column(name = "result_url")
    private String resultUrl;

    /**
     * 渠道状态：0 使用中，1已停用
     */
    private Integer status;

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
     * 公钥
     */
    @Column(name = "public_key")
    private String publicKey;

    /**
     * 私钥
     */
    @Column(name = "private_key")
    private String privateKey;

    /**
     * 每日收款上限
     */
    @Column(name = "daily_limit")
    private Integer dailyLimit;

    /**
     * 收款限额
     */
    @Column(name = "limit_amount")
    private BigDecimal limitAmount;


    /**
     * 备注
     */
    private String remark;

    /**
     * 额度分类，0：普通充值（默认），1：大额充值
     */
    @Column(name = "limit_type")
    private Integer limitType;

    /**
     * 是否启用动态脚本 0：不启用（默认），1：启用
     */
    @Column(name = "enable_script")
    private Integer enableScript;

    @Column(name = "script")
    private String script;

    @Column(name = "script_sign")
    private String scriptSign;

    /**
     * 币种
     */
    @Column(name = "coin")
    private String coin;

    /**
     * 今日收款金额
     */
    @Transient
    private Long successAmount;

    /**
     * 成功率（万分之）
     */
    @Transient
    private Integer successRate;

    /**
     * 充值记录条数
     */
    @Transient
    private Integer total;

    /**
     * 充值成功记录条数
     */
    @Transient
    private Integer success;

    @Transient
    private Long leftAmount;

}