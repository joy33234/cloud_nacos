package com.seektop.fund.service.impl;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponse.Builder;
import com.seektop.fund.mapper.GlFundUserCoinAccountChangeRecordMapper;
import com.seektop.fund.service.GlCoinChangeRecordService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

@DubboService(timeout = 5000, interfaceClass = GlCoinChangeRecordService.class)
public class GlCoinChangeRecordServiceImpl implements GlCoinChangeRecordService {
  @Autowired
  private GlFundUserCoinAccountChangeRecordMapper glFundUserCoinAccountChangeRecordMapper;
  @Override
  public RPCResponse<Boolean> findRecord(Integer userId, String orderId) {
    Builder<Boolean> newBuilder = RPCResponse.newBuilder();
    Integer count = glFundUserCoinAccountChangeRecordMapper.countByTradeId(userId, orderId);
    if(count == null || count == 0){
      return newBuilder.success().setData(false).build();
    }
    return newBuilder.success().setData(true).build();
  }
}
