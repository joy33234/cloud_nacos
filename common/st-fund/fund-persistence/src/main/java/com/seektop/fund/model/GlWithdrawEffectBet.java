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
 * 用户提现流水信息
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_effect_bet")
public class GlWithdrawEffectBet implements Serializable {

    private static final long serialVersionUID = 7320846579438046126L;


    @Id
    @Column(name = "id")
    private Integer id;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 币种编码
     */
    @Column(name = "coin")
    private String coin;

    /**
     * 提现所需流水
     */
    @Column(name = "required_bet")
    private BigDecimal requiredBet;

    /**
     * 上一次完成流水之后，账户累计变动金额
     */
    @Column(name = "grand_total_balance")
    private BigDecimal grandTotalBalance;

    /**
     * 流水累计开始时间（上一次资金变动，流水已完成时间）
     */
    @Column(name = "effect_start_time")
    private Date effectStartTime;

    /**
     * 输光清零标识：输光逻辑上线之后，用户是否已完成所需流水(0：未完成、1：已完成)
     */
    @Column(name = "lose_clean")
    private Integer loseClean;

    /**
     * 上次提款后，账户累计变动金额
     */
    @Column(name = "last_total_balance")
    private BigDecimal lastTotalBalance;

    /**
     * 输光逻辑输赢开始统计时间（上次提款后，第一次资金变动完成时间）
     */
    @Column(name = "lose_start_time")
    private Date loseStartTime;

    /**
     * 上次提款后开始计算输光：提款后为true, 提款后第一次账变为false
     */
    @Column(name = "lose")
    private Boolean lose;
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


}