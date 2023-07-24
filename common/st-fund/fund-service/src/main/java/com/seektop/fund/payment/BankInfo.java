package com.seektop.fund.payment;

import lombok.Data;

import java.util.Date;

@Data
public class BankInfo {

    /**
     * 开户人姓名
     */
    private String name;

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
     * 订单过期时间
     */
    private Date expiredDate;

}
