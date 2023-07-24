package com.seektop.fund.enums;

import com.seektop.common.local.base.parse.LocalCommonConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FundLanguageDicEnum implements LocalCommonConfig {
    RECHARGE_METHOD("RECHARGE_PAYMENT_NAME_%s"),
    MERCHANT("MERCHANT_%s"),
    WITHDRAW_METHOD("WITHDRAW_PAYMENT_NAME_%s"),
    BANK("BANK_NAME_%s"),
    RECHARGE_CREATE_ORDER_ERROR("RECHARGE_CREATE_ORDER_ERROR_%s"),
    USDT_PROTOCOL_TYPE("USDT_PROTOCOL_TYPE_%s"),
    FUND_LEVEL("FUND_LEVEL_%s"),
    ;

    private String key;

    @Override
    public String getModule() {
        return "fund-dic";
    }

}
