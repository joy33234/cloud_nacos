package com.seektop.fund.service.impl;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.enumerate.fund.C2CEggTypeEnum;
import com.seektop.fund.common.C2COrderDetailResult;
import com.seektop.fund.dto.result.c2c.C2COrderDetail;
import com.seektop.fund.handler.C2CEggRecordHandler;
import com.seektop.fund.handler.C2COrderHandler;
import com.seektop.fund.service.C2COrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;

@Slf4j
@DubboService(timeout = 3000, interfaceClass = C2COrderService.class)
public class C2COrderServiceImpl implements C2COrderService {

    @Resource
    private C2COrderHandler c2COrderHandler;
    @Resource
    private C2CEggRecordHandler c2CEggRecordHandler;

    @Override
    public RPCResponse<Boolean> isOpenRechargeEgg() {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        try {
            return newBuilder.success().setData(c2CEggRecordHandler.isOpen(C2CEggTypeEnum.RECHARGE)).build();
        } catch (Exception ex) {
            log.error("检查充值彩蛋是否开启时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    @Override
    public RPCResponse<Boolean> isOpenWithdrawEgg() {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        try {
            return newBuilder.success().setData(c2CEggRecordHandler.isOpen(C2CEggTypeEnum.WITHDRAW)).build();
        } catch (Exception ex) {
            log.error("检查提现彩蛋是否开启时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    @Override
    public RPCResponse<Boolean> checkAccordWithdrawEggActivity(String withdrawOrderId) {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        try {
            return newBuilder.success().setData(c2CEggRecordHandler.checkAccordWithdrawEggActivity(withdrawOrderId)).build();
        } catch (Exception ex) {
            log.error("检查提现订单{}是否满足提现彩蛋活动要求时发生异常", withdrawOrderId, ex);
            return newBuilder.fail().build();
        }
    }

    @Override
    public RPCResponse<Boolean> checkAccordRechargeEggActivity(String rechargeOrderId) {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        try {
            return newBuilder.success().setData(c2CEggRecordHandler.checkAccordRechargeEggActivity(rechargeOrderId)).build();
        } catch (Exception ex) {
            log.error("检查充值订单{}是否满足充值彩蛋活动要求时发生异常", rechargeOrderId, ex);
            return newBuilder.fail().build();
        }
    }

    @Override
    public RPCResponse<C2COrderDetail> getByWithdrawOrderId(String withdrawOrderId) {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        try {
            C2COrderDetailResult orderDetailResult = c2COrderHandler.getByWithdrawOrderId(withdrawOrderId);
            if (ObjectUtils.isEmpty(orderDetailResult)) {
                return newBuilder.fail().setMessage("订单号不存在").build();
            }
            C2COrderDetail detail = new C2COrderDetail();
            BeanUtils.copyProperties(orderDetailResult, detail);
            return newBuilder.success().setData(detail).build();
        } catch (Exception ex) {
            log.error("通过提现订单{}查询撮合详情发生异常", withdrawOrderId, ex);
            return newBuilder.fail().build();
        }
    }

    @Override
    public RPCResponse<C2COrderDetail> getByRechargeOrderId(String rechargeOrderId) {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        try {
            C2COrderDetailResult orderDetailResult = c2COrderHandler.getByRechargeOrderId(rechargeOrderId);
            if (ObjectUtils.isEmpty(orderDetailResult)) {
                return newBuilder.fail().setMessage("订单号不存在").build();
            }
            C2COrderDetail detail = new C2COrderDetail();
            BeanUtils.copyProperties(orderDetailResult, detail);
            return newBuilder.success().setData(detail).build();
        } catch (Exception ex) {
            log.error("通过充值订单{}查询撮合详情发生异常", rechargeOrderId, ex);
            return newBuilder.fail().build();
        }
    }

}