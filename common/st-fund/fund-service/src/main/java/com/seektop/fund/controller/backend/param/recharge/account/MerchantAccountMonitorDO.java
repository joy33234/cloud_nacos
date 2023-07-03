package com.seektop.fund.controller.backend.param.recharge.account;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAccountMonitorDO implements Serializable {

    /**
     * 时间戳
     */
    @NotNull(message = "参数异常:timeStamp Not Null")
    private String timeStamp;

    /**
     * 签名
     */
    @NotNull(message = "参数异常:sign Not Null")
    private String sign;

    /**
     * 时间范围
     */
    @NotNull(message = "参数异常:minuteDiff Not Null")
    private Integer minuteDiff;

    public String ip;
}
