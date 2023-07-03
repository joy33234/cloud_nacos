package com.seektop.fund.dto.param.proxy;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TransferDO implements Serializable {

    private static final long serialVersionUID = 670088220767587701L;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 操作代理ID
     */
    private Integer proxyId;

    /**
     * 目标用户ID
     */
    private Integer targetUserId;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 申请人
     */
    private String creator;
}
