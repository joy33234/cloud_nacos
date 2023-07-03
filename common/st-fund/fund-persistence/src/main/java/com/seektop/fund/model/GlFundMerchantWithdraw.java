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

/**
 * 出款商户余额信息
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_fund_merchant_withdraw")
public class GlFundMerchantWithdraw implements Serializable {

    /**
     * 三方商户号ID
     */
    @Id
    @Column(name = "merchant_id")
    private Integer merchantId;

    /**
     * 账户余额
     */
    @Column(name = "balance")
    private BigDecimal balance;

    /**
     * 更新时间
     */
    @Column(name = "last_update")
    private Date updateDate;

    /**
     * 银行ID
     */
    @Column(name = "bank_id")
    private Integer bankId;

    /**
     * 银行名称
     */
    @Column(name = "bank_name")
    private String bankName;

    /**
     * 银行卡号
     */
    @Column(name = "bank_card_no")
    private String bankCardNo;

    /**
     * 收款账户名称
     */
    @Column(name = "bank_account_name")
    private String bankAccountName;

    /**
     * 开户支行名称
     */
    @Column(name = "bank_branch_name")
    private String bankBranchName;

    /**
     * 预警状态(0正常 1低额预警 2高额预警)
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 单笔限额
     */
    @Column(name = "single_limit")
    private BigDecimal singleLimit;

}