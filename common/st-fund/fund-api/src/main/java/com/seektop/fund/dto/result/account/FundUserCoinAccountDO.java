package com.seektop.fund.dto.result.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FundUserCoinAccountDO implements Serializable {

    private static final long serialVersionUID = 1592961525817198907L;

    /**
     * 用户id
     */
    private Integer userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 金币余额
     */
    private Integer coinBalance;

    /**
     * 更新时间
     */
    private Date lastUpdate;

    /**
     * 上次金币变动原因
     */
    private String lastUpdateRemark;

    /**
     * 冗余字段，当前服务器时间
     */
    private long serverTime;
}