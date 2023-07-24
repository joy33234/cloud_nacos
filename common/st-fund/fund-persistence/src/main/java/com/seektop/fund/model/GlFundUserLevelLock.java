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
 * 用户层级锁定
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gl_fund_userlevellock")
public class GlFundUserLevelLock implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 7941746153854324059L;

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
     * 层级ID
     */
    @Column(name = "level_id")
    private Integer levelId;

    /**
     * 注册时间
     */
    @Column(name = "register_date")
    private Date registerDate;

    /**
     * 存款次数
     */
    @Column(name = "recharge_times")
    private Integer rechargeTimes;

    /**
     * 存款总额
     */
    @Column(name = "recharge_total")
    private BigDecimal rechargeTotal;

    /**
     * 提现次数
     */
    @Column(name = "withdraw_times")
    private Integer withdrawTimes;

    /**
     * 提现总额
     */
    @Column(name = "withdraw_total")
    private BigDecimal withdrawTotal;

    /**
     * 投注总额
     */
    @Column(name = "bet_total")
    private BigDecimal betTotal;

    /**
     * 统计日期：yyyy-MM-dd
     */
    @Column(name = "stat_date")
    private Date statDate;

    /**
     * 锁定状态
     * <p>
     * 0未锁定
     * 1已锁定
     */
    @Column(name = "status")
    private Integer status;

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

}