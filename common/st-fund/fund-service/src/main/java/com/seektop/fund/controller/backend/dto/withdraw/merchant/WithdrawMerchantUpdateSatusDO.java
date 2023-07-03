package com.seektop.fund.controller.backend.dto.withdraw.merchant;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
public class WithdrawMerchantUpdateSatusDO implements Serializable {


    /**
     * 账号ID
     */
    @NotNull(message = "参数异常:merchantId Not Null")
    private Integer merchantId;

    /**
     * 商户状态：0上架，1下架
     * 开启状态：0 已开启、 1 已关闭
     */
    @NotNull(message = "参数异常:status Not Null")
    private Integer status;

}
