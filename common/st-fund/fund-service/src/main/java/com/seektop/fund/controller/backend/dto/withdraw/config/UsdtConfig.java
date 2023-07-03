package com.seektop.fund.controller.backend.dto.withdraw.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;

/**
 * 数字货币提现设置
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class UsdtConfig implements Serializable {

    /**
     * 0-全部用户可提现、1-全部用户不可提现、2-仅充值过的用户可提现
     */
    @NotNull(message = "参数异常:status Not Null")
    private int status;

    /**
     * USDT提现支持的钱包协议
     */
    private Set<String> protocols;
}
