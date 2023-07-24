package com.seektop.fund.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户中心钱包记录
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gl_fund_useraccount")
public class GlFundUserAccount implements Serializable {

    private static final long serialVersionUID = 3576454234360772463L;

    /**
     * 用户ID
     */
    @Id
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 余额
     */
    @Column(name = "balance")
    private BigDecimal balance;

    /**
     * 可用流水
     */
    @Column(name = "valid_balance")
    private BigDecimal validBalance;

    /**
     * 冻结金额
     */
    @Column(name = "freeze_balance")
    private BigDecimal freezeBalance;

    /**
     * 更新
     */
    @Column(name = "last_update")
    private Date lastUpdate;

    /**
     * 上一次充值成功时间/上次流水调整后，剩余流水为0的时间
     */
    @Column(name = "last_recharge")
    private Date lastRecharge;

}