package com.seektop.fund.controller.partner.result;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderTextResponse implements Serializable {
    private static final long serialVersionUID = 4257524957912122985L;

    /**
     * 订单号
     */
    private String orderId;
    /**
     * 充值金额
     */
    private BigDecimal amount;
    /**
     * 是否URL
     */
    private Boolean isUrl;
    /**
     * 内容：URL或HTML
     */
    private String content;
}
