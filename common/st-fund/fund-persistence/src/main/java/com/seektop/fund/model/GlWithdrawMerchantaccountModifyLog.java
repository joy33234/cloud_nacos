package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"script"})
@Table(name = "gl_withdraw_merchantaccount_modify_log")
public class GlWithdrawMerchantaccountModifyLog {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;


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
     * 创建人
     */
    @Column(name = "creator")
    private String creator;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

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
     * 每日收款上限
     */
    @Column(name = "daily_limit")
    private Integer dailyLimit;

    /**
     * 操作类型，0：新增，1：更新  2：更新脚本， 3：删除
     */
    @Column(name = "modify_type")
    private Integer modifyType;

    /**
     * 渠道脚本
     */
    @Column(name = "script")
    private String script;

    /**
     * 支付代付的银行id
     */
    @Column(name = "bank_id")
    private String bankId;

}