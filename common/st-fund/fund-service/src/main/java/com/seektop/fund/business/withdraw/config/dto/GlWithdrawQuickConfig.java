package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawQuickConfig extends GlWithdrawCommonConfig {

    private List<GlWithdrawRule> sportRuleList; //体育流水赠送配置
    private List<GlWithdrawRule> funGameRuleList;//娱乐类流水赠送配置

    private int countLimit;//每天提现次数上限
    private String feeType; // 手续费类型：fix固定金额，percent百分比
    private BigDecimal fee; // 手续费
    private BigDecimal minLimit; // 提现最低限额
    private BigDecimal maxLimit; // 提现最高限额
    private int leftFreeCount; // 剩余免费提现次数
    private int withdrawCount; // 今天已使用提现次数
    private int freeTimes; // 免费提现次数
    private List<GlWithdrawSplitRule> splitRuleList; //提现拆单设置

    private int leftWithdrawCount;//当天剩余提现次数
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