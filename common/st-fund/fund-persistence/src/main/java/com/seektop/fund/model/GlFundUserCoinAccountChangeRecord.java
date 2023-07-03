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
@Table(name = "gl_fund_user_coin_account_change_record")
public class GlFundUserCoinAccountChangeRecord implements Serializable {

    private static final long serialVersionUID = 9184374614031632079L;

    @Id
    @Column(name = "record_id")
    private Integer recordId;

    /**
     * 交易id
     */
    @Column(name = "trade_id")
    private String tradeId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "user_name")
    private String userName;

    /**
     * 余额
     */
    @Column(name = "amount")
    private Integer amount;

    /**
     * 变动前余额
     */
    @Column(name = "before_balance")
    private Integer beforeBalance;

    /**
     * 变动后余额
     */
    @Column(name = "after_balance")
    private Integer afterBalance;

    /**
     * 更新时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 更新人
     */
    @Column(name = "creator")
    private String creator;

}