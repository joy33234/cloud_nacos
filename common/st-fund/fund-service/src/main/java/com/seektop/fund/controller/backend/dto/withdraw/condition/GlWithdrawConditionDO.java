package com.seektop.fund.controller.backend.dto.withdraw.condition;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class GlWithdrawConditionDO implements Serializable {


    private static final long serialVersionUID = 6418192718012929460L;

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 条件名称
     */
    private String conditionName;

    /**
     * 用户层级ID
     */
    private String levelId;

    private String levelName;

    /**
     * 最小金额
     */
    private BigDecimal minAmount;

    /**
     * 最大金额
     */
    private BigDecimal maxAmount;

    /**
     * 提现单类型（0-人工出款、1-自动出款）
     */
    private Integer withdrawType;

    /**
     * 条件状态(0-已删除、1-有效)
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    private Date createDate;

    private String creator;

    private Date lastUpdate;

    private String lastOperator;

}