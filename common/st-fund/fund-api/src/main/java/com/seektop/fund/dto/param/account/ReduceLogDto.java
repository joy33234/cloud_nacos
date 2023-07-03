package com.seektop.fund.dto.param.account;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ReduceLogDto implements Serializable {
    private static final long serialVersionUID = 8333105329716715648L;

    private String tradeId;
    private Integer userId;
    private String username;
    private Integer userType;

    /**
     * 游戏渠道
     */
    private String channelName;
    /**
     * 减币金额
     */
    private BigDecimal amount;
}
