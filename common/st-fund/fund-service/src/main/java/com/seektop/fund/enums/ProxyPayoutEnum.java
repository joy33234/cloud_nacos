package com.seektop.fund.enums;

import java.util.ArrayList;
import java.util.List;

public enum ProxyPayoutEnum {
    RECHARGE(0, "会员代充"),
    TRANSFER(1, "代理转账"),
    TRANSFER_MEMBER(2, "代理转账会员账户");

    private int code;
    private String message;

    ProxyPayoutEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    // valueList List
    public final static List<Integer> valueList;

    static {
        valueList = new ArrayList<>();
        for (ProxyPayoutEnum p : ProxyPayoutEnum.values()) {
            valueList.add(p.code);
        }
    }
}
