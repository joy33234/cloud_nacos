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

@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_payment_channelbank")
public class GlPaymentChannelBank implements Serializable {

    private static final long serialVersionUID = 3040893530063166711L;
    /**
     * 充值银行ID
     */
    @Id
    @Column(name = "paybank_id")
    private Integer paybankId;

    /**
     * 渠道ID
     */
    @Column(name = "channel_id")
    private Integer channelId;

    /**
     * 银行ID
     */
    @Column(name = "bank_id")
    private Integer bankId;

    /**
     * 银行编码
     */
    @Column(name = "bank_code")
    private String bankCode;

    /**
     * 银行名称
     */
    @Column(name = "bank_name")
    private String bankName;

    /**
     * 银行排序
     */
    private Integer sort;

    /**
     * 银行状态：0正常，1禁用
     */
    private Integer status;

    /**
     * 最低限额
     */
    @Column(name = "min_amount")
    private BigDecimal minAmount;

    /**
     * 最高限额
     */
    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    @Column(name = "last_update")
    private Date lastUpdate;

    @Column(name = "last_operator")
    private String lastOperator;

}