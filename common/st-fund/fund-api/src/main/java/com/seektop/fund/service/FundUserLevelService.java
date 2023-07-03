package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.userLevel.BlackUserLockDO;
import com.seektop.fund.dto.result.userLevel.BlackUserLockResult;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
import com.seektop.fund.dto.result.userLevel.UserLevelDO;

import java.util.Date;
import java.util.List;

public interface FundUserLevelService {

    /**
     * 获取用户财务等级相关信息
     *
     * @param userId
     * @return
     */
    RPCResponse<FundUserLevelDO> getFundUserLevel(Integer userId);

    /**
     * 层级名称
     * @param userId
     * @return
     */
    RPCResponse<String> getUserLevelName(Integer userId);

    /**
     * 根据userIds获取用户的层级
     * @param userIds
     * @return
     */
    RPCResponse<List<UserLevelDO>> findByUserIds(List<Integer> userIds);

    /**
     * 黑名单触发锁定用户层级
     *
     * @param lockDO
     * @return
     */
    RPCResponse<BlackUserLockResult> blackUserLock(BlackUserLockDO lockDO) throws GlobalException;

    /**
     * 充值和有效流水满足自动变更层级
     *
     * @param runningDate
     * @param size
     * @return
     */
    RPCResponse<Void> rechargeAndBettingUserLevelUpdateCheck(Date runningDate, Integer size);

    /**
     * 获取指定层级下的用户信息
     *
     * @param levelId
     * @param page
     * @param size
     * @return
     */
    RPCResponse<List<Integer>> findUserByLevelId(Integer levelId, Integer page, Integer size);

}