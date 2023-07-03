package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

/**
 * 银行卡验证记录
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_bankcardverify")
public class WithdrawBankCardVerify implements Serializable {
    private static final long serialVersionUID = 2813546121303515216L;
    /**
     * 银行卡ID
     */
    @Id
    @Column(name = "id")
    private Integer id;

    /**
     * 卡号
     */
    @Column(name = "card_no")
    private String cardNo;

    /**
     * 银行ID
     */
    @Column(name = "bank_id")
    private Integer bankId;

    /**
     * 银行名称
     */
    @Column(name = "bank_name")
    private String bankName;

    /**
     * 开户人姓名
     */
    private String name;

    /**
     * 开户省市区
     */
    private String address;


    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createDate;
}