package com.seektop.fund.controller.backend.result.recharge;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class RechargeSuccessApproveResult implements Serializable {


    private static final long serialVersionUID = 557768311002058390L;

    /**
     * 订单ID
     */
    private String orderId;

    /**
     * 审核人ID
     */
    private Integer userId;

    /**
     * 审核人用户名
     */
    private String username;

    /**
     * 审核金额
     */
    private BigDecimal amount;

    /**
     * 审核备注
     */
    private String remark;

    /**
     * 审核状态：1同意，2拒绝
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createDate;

}