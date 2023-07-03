package com.seektop.fund.controller.partner.result;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class OrderStatusResponse implements Serializable {
    private static final long serialVersionUID = 3020713719534454117L;

    private String orderId;

    /**
     * 支付状态：0：待支付，1：支付成功，2：支付失败，3：补单审核中
     */
    private Integer status;
    /**
     * 支付子状态，用于对状态的补充，根据场景细分列表的展示
     * 1：支付成功，2：补单审核成功，3：补单审核拒绝，4：人工拒绝补单，5：用户撤销，6：超时撤销
     */
    private Integer subStatus;

    private BigDecimal amount;

    private BigDecimal fee;

    private Date payTime;
}
