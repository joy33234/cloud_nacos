package com.seektop.fund.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class WithdrawMerchantBalanceResult implements Serializable {

    private static final long serialVersionUID = -6965370785319744377L;

    /**
     * 账号ID
     */
    private Integer merchantId;

    /**
     * 支付渠道ID
     */
    private Integer channelId;

    /**
     * 渠道名称
     */
    private String channelName;

    /**
     * 商户号
     */
    private String merchantCode;

    /**
     * 商户状态
     */
    private Integer status;

    /**
     * 三方商户余额
     */
    private BigDecimal balance;

    /**
     * 最后更新时间
     */
    private Date updateTime;

    /**
     * 币种
     */
    private String coin;
}