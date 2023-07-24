package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

/**
 * 提现支持银行
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_bank")
public class GlWithdrawBank implements Serializable {

    /**
     * 银行ID
     */
    @Id
    @Column(name = "bank_id")
    private Integer bankId;

    /**
     * 银行名称
     */
    @Column(name = "bank_name")
    private String bankName;

    /**
     * 银行网站地址
     */
    @Column(name = "bank_url")
    private String bankUrl;

    /**
     * 银行LOGO
     */
    @Column(name = "bank_logo")
    private String bankLogo;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 银行状态：0使用中，1已禁用
     */
    private Integer status;

    /**
     * 银行所属币种
     */
    @Column(name = "coin")
    private String coin;

}