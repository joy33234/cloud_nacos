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
@Table(name = "gl_recharge_receive_info")
public class GlRechargeReceiveInfo implements Serializable {


    private static final long serialVersionUID = -918929908807578781L;

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
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 收款账户人
     */
    private String owner;

    /**
     * 收款银行ID
     */
    @Column(name = "bankcard_id")
    private Integer bankcardId;

    /**
     * 收款银行名称
     */
    @Column(name = "bankcard_name")
    private String bankcardName;

    /**
     * 收款银行卡开户行
     */
    @Column(name = "bankcard_branch")
    private String bankcardBranch;

    /**
     * 收款银行卡号
     */
    @Column(name = "bankcard_no")
    private String bankcardNo;

    /**
     * 附言
     */
    private String keyword;

    /**
     * 本币数量
     */
    @Column(name = "digital_amount")
    private BigDecimal digitalAmount;

    /**
     * 区块协议
     */
    private String protocol;

    /**
     * USDT收币地址
     */
    @Column(name = "block_address")
    private String blockAddress;

    /**
     * USDT兑RMB——订单发起时汇率
     */
    private BigDecimal rate;

    /**
     * USDT对RMB——交易时汇率
     */
    @Column(name = "real_rate")
    private BigDecimal realRate;

    /**
     * 订单显示方式:NORMAL,DETAIL,DIGITAL
     */
    @Column(name = "show_type")
    private String showType;


    /**
     * 订单创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 订单过期时间
     */
    @Column(name = "expired_date")
    private Date expiredDate;

    /**
     * 交易Hash
     */
    @Column(name = "tx_hash")
    private String txHash;

    /**
     * 收币钱包ID
     */
    @Column(name = "receive_wallet_id")
    private Integer receiveWalletId;

}