package com.seektop.fund.controller.forehead.result;

import com.seektop.fund.business.withdraw.config.dto.*;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawCardResult;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawUsdtResult;
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
public class GlWithdrawInfoResult implements Serializable {

    private static final long serialVersionUID = 3801938461925328130L;

    /**
     * 开户人姓名
     */
    private String name;

    /**
     * 手机号（已加密）
     */
    private String mobile;

    /**
     * 是否需要短信验证
     */
    private boolean needSms;

    /**
     * 余额
     */
    private BigDecimal balance;

    /**
     * 剩余流水
     */

    private BigDecimal leftAmount;

    /**
     * 所需流水
     */
    private BigDecimal requireAmount;

    /**
     * 提现相关配置
     */
    private GlWithdrawConfig config;

    /**
     * 普通提现配置
     */
    private GlWithdrawGeneralConfig generalConfig;

    /**
     * 快速提现配置
     */
    private GlWithdrawQuickConfig quickConfig;

    /**
     * 代理提现配置
     */
    private GlWithdrawProxyConfig proxyConfig;

    /**
     * 极速提现配置
     */
    private GlC2CWithdrawConfig c2CWithdrawConfig;

    /**
     * 银行卡列表
     */
    private List<GlWithdrawCardResult> cardList;

    /**
     * 今日提现次数
     */
    private int withdrawTimes;

    /**
     * 今日已提现总额
     */
    private BigDecimal withdrawSumAmount;

    /**
     * 体育类流水: 下个等级赠送所需完成流水
     */
    private BigDecimal sportFreezeBalance;

    /**
     * 娱乐类流水: 下个等级赠送所需完成流水
     */
    private BigDecimal funFreezeBalance;

    private BigDecimal agentValidAmount; //代理可提现金额

    /**
     * 提示说明开关 1-开启、0-关闭
     */
    private String tipStatus;

    /**
     * usdt提现是否开启:true 开启、false关闭
     */
    private Boolean usdtWithdrawOpen;

    /**
     * USDT汇率
     */
    private BigDecimal usdtWithdrawRate;

    /**
     * 银行卡列表
     */
    private List<GlWithdrawUsdtResult> usdtList;

    /**
     * 银行卡提现是否开启
     */
    private Boolean bankWithdrawOpen = true;

    private String withdrawCloseTitle;

    private String withdrawCloseValue;

    private Set<String> protocols;

    /**
     * 极速（ctoc）提现是否开启:true 开启、false关闭
     */
    private Boolean c2CWithdrawOpen;
}
