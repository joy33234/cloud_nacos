package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 人工出款条件
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_condition")
public class GlWithdrawCondition implements Serializable {

    private static final long serialVersionUID = 5718762071876565255L;

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 条件名称
     */
    @Column(name = "condition_name")
    private String conditionName;

    /**
     * 用户层级ID
     */
    @Column(name = "level_id")
    private String levelId;

    /**
     * 最小金额
     */
    @Column(name = "min_amount")
    private BigDecimal minAmount;

    /**
     * 最大金额
     */
    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    /**
     * 提现单类型（0-人工出款、1-自动出款）
     */
    @Column(name = "withdraw_type")
    private Integer withdrawType;

    /**
     * 条件状态(0-已删除、1-有效)
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 备注
     */
    @Column(name = "remark")
    private String remark;

    @Column(name = "create_date")
    private Date createDate;

    @Column(name = "creator")
    private String creator;

    @Column(name = "last_update")
    private Date lastUpdate;

    @Column(name = "last_operator")
    private String lastOperator;

    /**
     * 币种
     */
    @Column(name = "coin")
    private String coin;

}