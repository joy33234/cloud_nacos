package com.ruoyi.okx.params.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiseDto {

    private String dateTime;

    private String modeType;

    /**
     * @see com.ruoyi.common.core.enums.Status
     *
     */
    private Integer status = 0; //0:正常  1：禁用 (交易状态，大盘模式每天仅交易一次)

    private Integer riseCount = 0;

    private BigDecimal risePercent = BigDecimal.ZERO;

    private Integer lowCount = 0;

    private BigDecimal lowPercent = BigDecimal.ZERO;

    private BigDecimal highest = BigDecimal.ZERO;

    private BigDecimal lowest = BigDecimal.ZERO;

    private Boolean riseBought = false; //当日大盘上涨是否交易过

    private Date riseBoughtTime;

    private Boolean fallBought = false; //当日大盘下跌是否交易过

    private Date fallBoughtTime;

    private BigDecimal BTCIns = BigDecimal.ZERO;

    private BigDecimal buyRisePercent = BigDecimal.ZERO;

    private BigDecimal buyLowPercent = BigDecimal.ZERO;

    private BigDecimal sellPercent = BigDecimal.ZERO;

    private Integer accountId;

    private String accountName;
    private String apikey;
    private String password;
    private String secretkey;

    private String orderType;


}
