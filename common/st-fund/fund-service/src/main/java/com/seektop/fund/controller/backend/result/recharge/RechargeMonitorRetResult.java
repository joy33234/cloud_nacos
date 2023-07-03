package com.seektop.fund.controller.backend.result.recharge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeMonitorRetResult implements Serializable {

    /**
     * 渠道ID
     */
    public Integer channelId;

    /**
     * 渠道名称
     */
    public String channelName;

    /**
     * 商户是否开启
     */
    public Integer openStatus;//1:开启，2:未开启

    /**
     * 失败订单数量
     */
    public Integer failOrderCount;

    /**
     * 三方商户操失败订单数量
     */
    public Integer failOrderThird;

    /**
     * 系统失败订单数量
     */
    public Integer failOrderSystem;


    private List<RechargeSuccessRateResult> successRateList;
}
