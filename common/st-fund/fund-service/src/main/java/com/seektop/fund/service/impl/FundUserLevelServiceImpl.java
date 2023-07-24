package com.seektop.fund.service.impl;

import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponse.Builder;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserLevelLockBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.common.UserFundUtils;
import com.seektop.fund.dto.param.userLevel.BlackUserLockDO;
import com.seektop.fund.dto.result.userLevel.BlackUserLockDetail;
import com.seektop.fund.dto.result.userLevel.BlackUserLockResult;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
import com.seektop.fund.dto.result.userLevel.UserLevelDO;
import com.seektop.fund.handler.RechargeBettingHandler;
import com.seektop.fund.model.GlFundUserLevelLock;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.service.FundUserLevelService;
import com.seektop.report.user.UserSynch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
@DubboService(timeout = 3000, interfaceClass = FundUserLevelService.class)
public class FundUserLevelServiceImpl implements FundUserLevelService {

    @Autowired
    private GlFundUserLevelLockBusiness glFundUserLevelLockBusiness;

    @Autowired
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Autowired
    private ReportService reportService;

    @Autowired
    private UserFundUtils userFundUtils;

    @Resource
    private RechargeBettingHandler rechargeBettingHandler;

    @Override
    public RPCResponse<FundUserLevelDO> getFundUserLevel(@NotNull(message = "用户ID不能为空") Integer userId) {
        Builder<FundUserLevelDO> newBuilder = RPCResponse.newBuilder();
        GlFundUserlevel userLevel = glFundUserlevelBusiness.getUserLevel(userId);

        FundUserLevelDO levelDO = new FundUserLevelDO();
        levelDO.setLevelId(userLevel.getLevelId());
        levelDO.setLevelName(userLevel.getName());
        levelDO.setLevelType(userLevel.getLevelType());
        return newBuilder.success().setData(levelDO).build();
    }

    @Override
    public RPCResponse<String> getUserLevelName(Integer userId) {
        String userLevelName = glFundUserlevelBusiness.getUserLevelName(userId);
        return RPCResponseUtils.buildSuccessRpcResponse(userLevelName);
    }

    @Override
    public RPCResponse<List<UserLevelDO>> findByUserIds(List<Integer> userIds) {
        List<UserLevelDO> userLevels = glFundUserlevelBusiness.getUserLevels(userIds);
        return RPCResponseUtils.buildSuccessRpcResponse(userLevels);
    }

    @Override
    public RPCResponse<BlackUserLockResult> blackUserLock(BlackUserLockDO lockDO) throws GlobalException {
        BlackUserLockResult result = new BlackUserLockResult();
        Builder<BlackUserLockResult> newBuilder = RPCResponse.newBuilder();
        if (CollectionUtils.isEmpty(lockDO.getUsers())) {
            return newBuilder.success().setData(result).build();
        }
        GlFundUserlevel willLockFundUserLevel = glFundUserlevelBusiness.findById(lockDO.getFundLevelId());
        if (ObjectUtils.isEmpty(willLockFundUserLevel)) {
            willLockFundUserLevel = glFundUserlevelBusiness.findById(1);
        }
        result.setOptData(willLockFundUserLevel.getName());
        List<BlackUserLockDetail> details = Lists.newArrayList();
        GlFundUserLevelLock currentLockLevel;
        GlFundUserlevel currentUserLevel = null;
        int successNum = 0;
        for (GlUserDO user : lockDO.getUsers()) {
            if (ObjectUtils.isEmpty(user)) {
                continue;
            }
            // 查询用户当前锁定的层级信息
            currentLockLevel = glFundUserLevelLockBusiness.findById(user.getId());
            if (currentLockLevel != null) {
                currentUserLevel = glFundUserlevelBusiness.findById(currentLockLevel.getLevelId());
            }
            if (currentUserLevel != null) {
                details.add(new BlackUserLockDetail(user.getId(), currentUserLevel.getLevelId(), currentUserLevel.getName()));
            } else {
                details.add(new BlackUserLockDetail(user.getId(), null, ""));
            }
            if (currentLockLevel == null) {
                currentLockLevel = new GlFundUserLevelLock();
                currentLockLevel.setCreateDate(new Date());
                currentLockLevel.setLevelId(willLockFundUserLevel.getLevelId());
                currentLockLevel.setUserId(user.getId());
                currentLockLevel.setUserName(user.getUsername());
                currentLockLevel.setLastOperator(lockDO.getOperator());
                currentLockLevel.setLastUpdate(new Date());
                currentLockLevel.setStatus(1);
                currentLockLevel.setStatDate(new Date());
                currentLockLevel.setWithdrawTotal(BigDecimal.ZERO);
                currentLockLevel.setWithdrawTimes(0);
                currentLockLevel.setRegisterDate(user.getRegisterDate());
                currentLockLevel.setRechargeTotal(BigDecimal.ZERO);
                currentLockLevel.setRechargeTimes(0);
                currentLockLevel.setBetTotal(BigDecimal.ZERO);
                glFundUserLevelLockBusiness.save(currentLockLevel);
            } else {
                currentLockLevel.setLevelId(willLockFundUserLevel.getLevelId());
                currentLockLevel.setUserName(user.getUsername());
                currentLockLevel.setStatus(1);
                currentLockLevel.setLastUpdate(new Date());
                currentLockLevel.setLastOperator(lockDO.getOperator());
                glFundUserLevelLockBusiness.updateByPrimaryKeySelective(currentLockLevel);
            }

            successNum++;
            // 上报ES同步用户信息
            UserSynch userSynch = new UserSynch();
            userSynch.setId(user.getId());
            userSynch.setLevel_id(willLockFundUserLevel.getLevelId());
            userSynch.setLevel_name(willLockFundUserLevel.getName());
            userSynch.setLevel_status(1);
            reportService.userSynch(userSynch);

            //更新用户层级Redis缓存
            userFundUtils.setCache(user.getId(), willLockFundUserLevel.getLevelId(), willLockFundUserLevel.getName(), willLockFundUserLevel.getLevelType());
        }
        result.setDetails(details);
        result.setSuccessNum(successNum);
        return newBuilder.success().setData(result).build();
    }

    @Override
    public RPCResponse<Void> rechargeAndBettingUserLevelUpdateCheck(Date runningDate, Integer size) {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        try {
            rechargeBettingHandler.executeConfigCheck(runningDate, size);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("执行按流水的层级变更检查时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    @Override
    public RPCResponse<List<Integer>> findUserByLevelId(Integer levelId, Integer page, Integer size) {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        try {
            List<GlFundUserLevelLock> userLevelLockList = glFundUserLevelLockBusiness.findByPage(levelId, page, size);
            if (CollectionUtils.isEmpty(userLevelLockList)) {
                return newBuilder.success().build();
            }
            List<Integer> resultList = Lists.newArrayList();
            for (GlFundUserLevelLock glFundUserLevelLock : userLevelLockList) {
                resultList.add(glFundUserLevelLock.getUserId());
            }
            return newBuilder.setData(resultList).success().build();
        } catch (Exception ex) {
            log.error("获取层级{} {} {}下的用户名时发生异常", levelId, page, size, ex);
            return newBuilder.fail().build();
        }
    }

}