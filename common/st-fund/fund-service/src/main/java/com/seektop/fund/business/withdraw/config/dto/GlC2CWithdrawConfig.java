package com.seektop.fund.business.withdraw.config.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 极速转卡充值设置
 */
@Data
public class GlC2CWithdrawConfig implements Serializable {

    /**
     * 已提现次数
     */
    private int withdrawCount;

    /**
     * 当天剩余提现次数
     */
    private int leftWithdrawCount;

    /**
     * 提现流水倍数
     */
    private int multiple;


    /**
     * 提现金额
     */
    private List<Integer> amounts;


    /**
     * vip等级
     */
    private List<Integer> withdrawVipLevels;

    /**
     *提现次数上限
     */
    private Integer withdrawDailyUseLimit;

    /**
     * 1：百分比
     * 2：固定金额
     */
    private Integer withdrawHandlingFeeType;

    private BigDecimal withdrawHandlingFeeValue;

    private BigDecimal withdrawHandlingFeeMax;

    private Integer withdrawReceiveConfirmAlertTime;

    private Integer withdrawReceiveConfirmAlertTimeout;

    private Integer matchWaitTime;




}
