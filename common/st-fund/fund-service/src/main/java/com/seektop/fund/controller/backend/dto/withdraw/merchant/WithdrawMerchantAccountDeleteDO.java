package com.seektop.fund.controller.backend.dto.withdraw.merchant;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
public class WithdrawMerchantAccountDeleteDO implements Serializable {

    /**
     * 账号ID
     */
    @NotNull(message = "参数异常:merchantId Not Null")
    private Integer merchantId;

}
