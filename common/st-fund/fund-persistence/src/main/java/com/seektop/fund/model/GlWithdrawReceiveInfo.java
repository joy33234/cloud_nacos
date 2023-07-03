package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_receive_info")
public class GlWithdrawReceiveInfo implements Serializable {

    /**
     * 订单ID
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 三方商户订单号
     */
    @Column(name = "third_order_id")
    private String thirdOrderId;

    /**
     * 区块协议
     */
    private String protocol;

    /**
     * 钱包地址
     */
    private String address;

    /**
     * 提现金额
     */
    private BigDecimal amount;

    /**
     * 提现手续费
     */
    private BigDecimal fee;

    /**
     * 预计到账USDT金额
     */
    @Column(name = "usdt_amount")
    private BigDecimal usdtAmount;

    /**
     * 预计汇率
     */
    private BigDecimal rate;

    /**
     * 实际到账USDT金额
     */
    @Column(name = "actual_usdt_amount")
    private BigDecimal actualUsdtAmount;

    /**
     * 实际到账汇率
     */
    @Column(name = "actual_rate")
    private BigDecimal actualRate;

    /**
     * 订单创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 订单过期时间
     */
    @Column(name = "update_date")
    private Date updateDate;

    /**
     * 交易哈希值
     */
    @Column(name = "tx_hash")
    private String txHash;

    /**
     * 付款时间
     */
    @Column(name = "payout_date")
    private Date payoutDate;

}