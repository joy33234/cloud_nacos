package com.seektop.fund.payment;

import com.seektop.constant.FundConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by ken on 2018/5/14.
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlRechargeResult implements Serializable {


    private static final long serialVersionUID = 4280772263317245214L;

    /**
     * 交易订单号
     */
    private String tradeId;

    /**
     * 三方商户订单号
     */
    private String thirdOrderId;

    /**
     * 三方支付请求地址
     */
    private String redirectUrl;

    /**
     * html-Form表单
     */
    private String message;

    /**
     * 0:成功  1：系统异常   2: 三方异常
     */
    private int errorCode = FundConstant.RechargeErrorCode.NORMAL;

    private String errorMsg;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 订单金额
     */
    private BigDecimal amount;

    /**
     * 附言
     */
    private String keyword;

    /**
     * 转账充值-收款账户信息
     */
    private BankInfo bankInfo;

    /**
     * USDT充值-收款账户信息
     */
    private BlockInfo blockInfo;

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
     * 开户人姓名
     */
    private String name;

}
