package com.seektop.fund.controller.partner.result;

import com.seektop.fund.controller.forehead.result.RechargeDigitalInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderInfoResponse implements Serializable {
    private static final long serialVersionUID = -7482159578194328079L;

    /**
     * 订单ID
     */
    private String orderId;
    /**
     * 订单金额
     */
    private BigDecimal amount;
    /**
     * 附言
     */
    private String keyword;
    /**
     * 银行ID
     */
    private Integer bankId;
    /**
     * 银行名称
     */
    private String bankName;
    /**
     * 开户支行
     */
    private String bankBranchName;
    /**
     * 银行卡号
     */
    private String cardNo;
    /**
     * 开户人姓名
     */
    private String name;

    /**
     * 数字货币信息
     */
    private RechargeDigitalInfo digitalInfo;
}
