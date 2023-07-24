package com.seektop.fund.controller.forehead.param.proxy;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProxyPayoutDO implements Serializable {

    private String targetId;

    @NotNull(message = "金额不能为空")
    private long amount;

    private String password;

    @NotNull(message = "操作类型不能为空")
    private Integer type;

    @NotNull(message = "币种不能为空")
    private String coinCode;

    private String remarks;
}
