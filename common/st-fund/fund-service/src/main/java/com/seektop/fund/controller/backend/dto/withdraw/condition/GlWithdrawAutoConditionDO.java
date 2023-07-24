package com.seektop.fund.controller.backend.dto.withdraw.condition;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class GlWithdrawAutoConditionDO implements Serializable {

    private static final long serialVersionUID = 4024950474180050382L;
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

    /**
     * 用户层级名称
     */
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
     * 三方出款商户
     */
    private String merchantId;

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