package com.seektop.fund.controller.backend.dto.withdraw;

import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.model.WithdrawAutoConditionMerchantAccount;
import lombok.Data;

@Data
public class WithdrawMerchantAccountDO extends GlWithdrawMerchantAccount {
    private static final long serialVersionUID = 8425703558476403857L;

    /**
     * 自动出款商户设置
     */
    private WithdrawAutoConditionMerchantAccount merchantAccount;
}
