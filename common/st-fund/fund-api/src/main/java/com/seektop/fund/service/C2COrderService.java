package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.fund.dto.result.c2c.C2COrderDetail;

public interface C2COrderService {

    /**
     * 充值彩蛋是否开启
     *
     * @return
     */
    RPCResponse<Boolean> isOpenRechargeEgg();

    /**
     * 提现彩蛋是否开启
     *
     * @return
     */
    RPCResponse<Boolean> isOpenWithdrawEgg();

    /**
     * 检查提现订单是否满足彩蛋活动
     *
     * @param withdrawOrderId
     * @return
     */
    RPCResponse<Boolean> checkAccordWithdrawEggActivity(String withdrawOrderId);

    /**
     * 检查充值订单是否满足彩蛋活动
     *
     * @param rechargeOrderId
     * @return
     */
    RPCResponse<Boolean> checkAccordRechargeEggActivity(String rechargeOrderId);

    /**
     * 通过提现订单获取撮合订单详情
     *
     * @param withdrawOrderId
     * @return
     */
    RPCResponse<C2COrderDetail> getByWithdrawOrderId(String withdrawOrderId);

    /**
     * 通过充值订单获取撮合订单详情
     *
     * @param rechargeOrderId
     * @return
     */
    RPCResponse<C2COrderDetail> getByRechargeOrderId(String rechargeOrderId);

}