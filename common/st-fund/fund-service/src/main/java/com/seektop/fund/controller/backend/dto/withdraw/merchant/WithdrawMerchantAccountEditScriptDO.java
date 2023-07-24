package com.seektop.fund.controller.backend.dto.withdraw.merchant;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
@ToString
public class WithdrawMerchantAccountEditScriptDO implements Serializable {


    /**
     * 账号ID
     */
    @NotNull(message = "参数异常:merchantId Not Null")
    private Integer merchantId;

    /**
     * 提现渠道ID
     */
    @NotNull(message = "参数异常:channelId Not Null")
    private Integer channelId;


    /**
     * 提现渠道脚本
     */
    @NotNull(message = "参数异常:script Not Null")
    private String script;

}
