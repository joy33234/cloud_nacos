package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;

import java.util.List;

public interface FundUserLevelLockService {

    /**
     * 用户层级每日定时更新
     * @return
     */
    RPCResponse<Boolean> doUserLevelClean();

    /**
     * 用户层级锁定
     * @return
     */
    RPCResponse<Boolean> doUserLevelLock(List<String> userNames, Integer levelId);
}
