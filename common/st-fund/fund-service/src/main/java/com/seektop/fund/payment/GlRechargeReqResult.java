package com.seektop.fund.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Created by ken on 2018/5/14.
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlRechargeReqResult {
    private String bankName;
    private String brankBranchName;
    private String name;
    private String cardNo;
    private String bankUrl;
    private int expireTime;
    private long amount;
    private String keyword;
    private String tradeNo;
    private String redirectUrl;

    private String message;
    private String signature;
    private String payMethod;
    private String payUrl;
}
