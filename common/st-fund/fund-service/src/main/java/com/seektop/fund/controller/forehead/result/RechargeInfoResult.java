package com.seektop.fund.controller.forehead.result;

import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeInfoResult {

    /**
     * 手机号码
     */
    private String telephone;

    /**
     * 校验用户是否完善信息功能开关
     */
    private String onOff;

    /**
     * 用户银行卡信息
     */
    private List<GlWithdrawUserBankCard> bankCards;

    /**
     * 最后充值订单信息
     */
    private GlRechargeDO lastRecharge;

    /**
     * 充值订单号
     */
    private String orderId;

    /**
     * 币种
     */
    private String coin;

    /**
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 充值方式ID
     */
    private Integer paymentId;

    /**
     * 充值方式名称
     */
    private String paymentName;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 子状态
     */
    private Integer subStatus;

    /**
     * 充值订单显示方式
     */
    private String showType;

    /**
     * 是否存在待支付订单
     */
    private boolean existRecharge;

    /**
     * 充值订单显示收款卡详情信息
     */
    private RechargeBankInfo bankInfo;

    /**
     * 充值订单显示虚拟货币收款详情信息
     */
    private RechargeDigitalInfo digitalInfo;

    /**
     * 订单发起时间
     */
    private Date createDate;

    /**
     * 订单过期时间
     */
    private Date expiredDate;

    /**
     * 付款提醒时间
     */
    private Date alertTime;

    /**
     * 适应方式
     */
    private Integer agentType;

    /**
     * 付款时间
     */
    private Date paymentDate;

    /**
     * 订单过期时间 - ttl
     */
    private Long expiredTtl;
}
