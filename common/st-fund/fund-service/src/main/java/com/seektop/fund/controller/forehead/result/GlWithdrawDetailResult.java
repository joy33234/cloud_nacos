package com.seektop.fund.controller.forehead.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class GlWithdrawDetailResult implements Serializable {
    private static final long serialVersionUID = -1203628288938224637L;

    private String orderId;
    private String name;
    private String cardNo;
    private String bankName;
    private String coin;
    private BigDecimal amount;
    private Integer status;
    private BigDecimal fee;
    private String address;
    private Date createDate;
    private Date lastUpdate;


    /**
     * 提现订单显示方式：BANK、DIGITAL
     */
    private String showType;

    /**
     * 钱包名称
     */
    private String nickName;

    /**
     * USDT协议
     */
    private String protocol;

    /**
     * 提币地址
     */
    private String usdtAddress;

    /**
     * USDT 交易汇率
     */
    private BigDecimal rate;

    /**
     * 大约到账USDT数量
     */
    private BigDecimal usdtAmount;

    /**
     * 收款超时时间
     */
    private Date expiredDate;

    /**
     * 到账提醒时间
     */
    private Date alertTime;

    /**
     * 提现申请类型（1-普通提现、2-快速提现、3-代理提现、4-极速提现）
     */
    private Integer type;

    /**
     * 收款超时确认时间
     */
    private Integer receiveTimeout;

    /**
     * 付款时间
     */
    private Date paymentDate;

    /**
     * 收款超时时间 - 秒
     */
    private Long expiredTtl;

    /**
     * 奖励金额
     */
    private BigDecimal awardAmount;

}
