package com.seektop.fund.controller.backend.param.recharge.account;

import lombok.*;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 更新商户脚本
 *
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MerchantAccountEditScriptDO implements Serializable {

    @NotNull(message = "参数异常:merchantId Not Null")
    private Integer merchantId;

    @NotNull(message = "参数异常:channelId Not Null")
    private Integer channelId;

    @NotNull(message = "参数异常:script Not Null")
    private String script;

}
