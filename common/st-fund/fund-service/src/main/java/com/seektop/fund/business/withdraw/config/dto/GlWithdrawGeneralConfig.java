package com.seektop.fund.business.withdraw.config.dto;

import com.seektop.fund.controller.backend.dto.withdraw.config.GeneralConfigLimitDO;
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
public class GlWithdrawGeneralConfig extends GlWithdrawCommonConfig {

    private int freeTimes; // 每日可免费提现次数
    private String feeType; // 手续费类型：fix固定金额，percent百分比
    private BigDecimal fee; // 手续费
    private BigDecimal feeLimit; // 最高手续费
    private int countLimit;//每天提现次数上限
    private BigDecimal minLimit; // 提现最低限额
    private BigDecimal maxLimit; // 提现最高限额
    private int withdrawCount; // 今天已使用提现次数
    private int leftFreeCount; // 当天剩余免费提现次数

    private int leftWithdrawCount;//当天剩余提现次数

    private BigDecimal minUSDTLimit; // 数字货币提现最低限额
    private BigDecimal maxUSDTLimit; // 数字货币提现最高限额

    /**
     * 层级限额设置
     */
    private List<GeneralConfigLimitDO> limitList;


    /**
     * USDT提现支持的钱包协议
     */
    private Set<String> protocols;

    /**
     * USDT提现根据协议限额
     */
    private List<GlWithdrawUSDTLimit> usdtLimits;

    private String coin;
}