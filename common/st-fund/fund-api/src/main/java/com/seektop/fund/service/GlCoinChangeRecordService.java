package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;

public interface GlCoinChangeRecordService {
  RPCResponse<Boolean> findRecord(Integer userId, String orderId);
}
