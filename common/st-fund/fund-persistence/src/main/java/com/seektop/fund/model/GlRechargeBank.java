package com.seektop.fund.model;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gl_recharge_bank")
public class GlRechargeBank implements Serializable {

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
    @Column(name = "sort")
    private Integer sort;

    /**
     * 银行状态
     *
     * 0：使用中
     * 1：已禁用
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 银行所属币种
     */
    @Column(name = "coin")
    private String coin;

}