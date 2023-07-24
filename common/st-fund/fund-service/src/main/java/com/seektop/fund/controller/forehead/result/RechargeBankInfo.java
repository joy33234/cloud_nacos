package com.seektop.fund.controller.forehead.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class RechargeBankInfo implements Serializable {

    /**
     * 订单号
     */
    private String orderId;

    /**
     * 收款账户人
     */
    private String owner;

    /**
     * 收款卡ID
     */
    private Integer bankcardId;

    /**
     * 收款银行名称
     */
    private String bankcardName;

    /**
     * 收款卡开户行
     */
    private String bankcardBranch;

    /**
     * 收款卡号
     */
    private String bankcardNo;

    /**
     * 附言
     */
    private String keyword;
}
