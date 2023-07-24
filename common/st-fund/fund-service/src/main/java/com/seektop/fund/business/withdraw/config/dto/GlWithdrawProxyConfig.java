package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawProxyConfig implements Serializable {

    private int countLimit;//每天提现次数上限
    private BigDecimal amountLimit; //每天提现金额上限
    private String feeType; // 手续费类型：fix固定金额，percent百分比
    private BigDecimal fee; // 手续费
    private BigDecimal minLimit; // 提现最低限额
    private BigDecimal maxLimit; // 提现最高限额
    private int withdrawCount; // 今天已使用提现次数
    private List<GlWithdrawSplitRule> splitRuleList;
    private BigDecimal minUSDTLimit; // 数字货币提现最低限额
    private BigDecimal maxUSDTLimit; // 数字货币提现最高限额

    /**
     * USDT提现支持的钱包协议
     */
    private Set<String> protocols;

    /**
     * USDT协议限额
     */
    private List<GlWithdrawUSDTLimit> usdtLimits;

    private String coin;


}