package com.seektop.fund.business.withdraw.config.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * 极速转卡充值设置
 */
@Data
public class GlC2CRechargeConfig implements Serializable {

    /**
     * 用户层级
     */
    private Set<Integer> levelIds;

    /**
     * 每日充值上限
     */
    private Integer dailyLimit;

    /**
     * 每日取消次数
     */
    private Integer cancelTimes;

    /**
     * 充值提醒时间
     */
    private Integer warnTime;

    /**
     * 付款超时时间
     */
    private Integer timeOut;

    /**
     * vip等级
     */
    private Set<Integer> vipLevels;
}
