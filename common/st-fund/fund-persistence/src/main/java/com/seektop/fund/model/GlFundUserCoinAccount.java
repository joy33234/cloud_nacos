package com.seektop.fund.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户中心钱包记录
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gl_fund_user_coin_account")
public class GlFundUserCoinAccount implements Serializable {

    private static final long serialVersionUID = -3660760637068913182L;

    /**
     * 用户ID
     */
    @Id
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 用户名
     */
    @Column(name = "user_name")
    private String userName;

    /**
     * 余额
     */
    @Column(name = "coin_balance")
    private Integer coinBalance;

    /**
     * 更新
     */
    @Column(name = "last_update")
    private Date lastUpdate;

    /**
     * 上一次充值成功时间/上次流水调整后，剩余流水为0的时间
     */
    @Column(name = "last_update_remark")
    private String lastUpdateRemark;

}