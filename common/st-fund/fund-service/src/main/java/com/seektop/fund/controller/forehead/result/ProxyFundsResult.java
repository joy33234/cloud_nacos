package com.seektop.fund.controller.forehead.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class ProxyFundsResult implements Serializable {

    private static final long serialVersionUID = 2402259945858598443L;
    private List<String> failUser = new ArrayList<>();
    private List<String> sucUser = new ArrayList<>();

    /**
     * 操作金额
     */
    private BigDecimal amount;

    /**
     * 转账前钱包余额
     */
    private BigDecimal balanceBefore;

    /**
     * 转账后钱包余额
     */
    private BigDecimal balanceAfter;

    /**
     * 转账后剩余信用额度
     */
    private BigDecimal creditAmountAfter;

    /**
     * 转账前代充额度
     */
    private BigDecimal creditBefore;

    /**
     * 转账后代充额度
     */
    private BigDecimal creditAfter;
}
