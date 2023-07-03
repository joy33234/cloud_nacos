package com.seektop.fund.controller.backend.dto.withdraw.merchant;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
public class WithdrawMerchantSearchDO extends ManageParamBaseDO implements Serializable {

    /**
     * 提现订单ID
     */
    @NotNull(message = "参数异常:orderId Not Null")
    private String orderId;

}
