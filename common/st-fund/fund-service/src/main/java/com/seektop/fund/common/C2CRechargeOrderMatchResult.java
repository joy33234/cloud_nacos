package com.seektop.fund.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class C2CRechargeOrderMatchResult implements Serializable {

    /**
     * 撮合结果
     *
     * 0：没有撮合到符合条件的订单
     * 1：撮合成功(指定金额)
     * 2：撮合成功(推荐金额)
     */
    private Integer matchedResult;

    /**
     * 推荐金额
     *
     * 当撮合结果等于2时该字段有值，其他情况下该字段值无效
     */
    private BigDecimal recommendAmount;

    /**
     * 返利金额
     */
    private BigDecimal awardAmount = BigDecimal.ZERO;

}