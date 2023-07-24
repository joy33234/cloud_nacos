package com.seektop.fund.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
public class C2CRechargeOrderCreateResult implements Serializable {

    /**
     * 提现单号
     */
    private String withdrawOrderId;

    /**
     * 充值单号
     */
    private String rechargeOrderId;

    /**
     * 状态
     *
     * @see com.seektop.enumerate.fund.C2CWithdrawOrderStatusEnum
     */
    private Short status;

    /**
     * 撮合订单创建结果
     *
     * 1：成功
     * 2：失败-充值订单已经存在其他的匹配
     * 3：失败-匹配不到提现订单
     * 4：失败-充值订单的金额不符合
     */
    private Integer code;

    /**
     * 错误消息
     */
    private String message;

}