package com.seektop.fund.dto.param.account;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
public class UserCoinAccountChangeDO implements Serializable {

    private static final long serialVersionUID = -5555604989785756510L;
    /**
     * 交易订单号
     */
    private String tradeId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 金币变动（加币为正、减币为负）
     */
    private Integer amount;

    /**
     * 变动说明
     */
    private String remark;

    /**
     * 操作人
     */
    private String operator = "system";
}
