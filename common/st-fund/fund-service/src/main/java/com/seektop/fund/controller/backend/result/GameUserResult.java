package com.seektop.fund.controller.backend.result;

import com.seektop.gamebet.dto.result.GameUserDO;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GameUserResult extends GameUserDO {
    private static final long serialVersionUID = -1604808506667263959L;

    /**
     * 输赢
     */
    private BigDecimal win;

    /**
     * 有效投注
     */
    private BigDecimal validAmount;

    /**
     * 转入 - 转出
     */

    private BigDecimal transferDiff;

    /**
     * 胜率：每个平台，会员赢钱的注单数量 / 会员总注单数量
     */
    private String winBetRate;

    /**
     * 低配注单占比：只显示体育、电竞相关平台，港赔低于0.7，欧赔低于1.7的注单占比总注单数
     */
    private String lowOddsBetRate;
    /**
     * 会员盈利率：每个平台， 会员输赢 / 有效投注
     */
    private String winRate;

    /**
     * 注单数量
     */
    private int betCount;
}
