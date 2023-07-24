package com.seektop.fund.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class WithdrawNotify implements Serializable {

    private static final long serialVersionUID = -460030879724785301L;

    /**
     * 订单号
     */
    private String orderId;
    /**
     * 商户返回出款状态：0成功，1失败,2处理中
     */
    private Integer status;
    /**
     * 三方交易号
     */
    private String thirdOrderId;
    /**
     * 成功金额
     */
    private BigDecimal amount;
    /**
     * 成功时间
     */
    private Date successTime;
    /**
     * 出款商户ID
     */
    private Integer merchantId;
    /**
     * 出款商户名称
     */
    private String merchantName;
    /**
     * 出款商户号
     */
    private String merchantCode;
    /**
     * 备注
     */
    private String remark;


    /**
     * 出款银行卡号
     */
    private String outCardNo;

    /**
     * 出款账户
     */
    private String outCardName;

    /**
     * 出款银行名称
     */
    private String outBankFlag;
    /**
     * 接到回调的响应信息
     */
    private String rsp;

    /**
     * 实际出款USDT金额
     */
    private BigDecimal actualAmount;

    /**
     * 实际出款USDT汇率
     */
    private BigDecimal actualRate;

}
