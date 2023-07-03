package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawPolicyConfig implements Serializable {

    private static final long serialVersionUID = -3169973049312276749L;

    //风控金额
    private List<GlWithdrawPolicyAmountConfig> list;

    private int sameIpCheck; // 同ip多账号检测:0关闭，1开启
    private int sameDeviceCheck; // 同设备多账号检测:0关闭，1开启
    private int time; //检测时间 以小时为单位
    private int registerDays; //注册时间  单位为天
}
