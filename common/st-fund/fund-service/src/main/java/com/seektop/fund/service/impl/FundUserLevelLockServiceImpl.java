package com.seektop.fund.service.impl;

import com.google.common.collect.Lists;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.dto.GlUserDO;
import com.seektop.fund.business.GlFundUserLevelLockBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.controller.backend.dto.FundUserLeveLockDto;
import com.seektop.fund.model.GlFundUserLevelLock;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.service.FundUserLevelLockService;
import com.seektop.user.service.GlUserService;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@DubboService(timeout = 5000, interfaceClass = FundUserLevelLockService.class)
public class FundUserLevelLockServiceImpl implements FundUserLevelLockService {

    @Autowired
    private GlFundUserLevelLockBusiness fundUserLevelLockBusiness;

    @Autowired
    private GlFundUserlevelBusiness fundUserlevelBusiness;

    @DubboReference(retries = 1, timeout = 5000)
    private GlUserService userService;

    @Override
    public RPCResponse<Boolean> doUserLevelClean() {
        Date now = new Date();
        Date fromDate = DateUtils.addDays(now, -30);

        Condition con = new Condition(GlFundUserLevelLock.class);
        con.createCriteria().andEqualTo("status", 0).andLessThanOrEqualTo("statDate", fromDate).andNotEqualTo("levelId", 1);
        List<GlFundUserLevelLock> lockList = fundUserLevelLockBusiness.findByCondition(con);
        if (lockList == null || lockList.isEmpty()) {
            return RPCResponseUtils.buildSuccessRpcResponse(true);
        }
        for (GlFundUserLevelLock lock : lockList) {
            lock.setLastUpdate(now);
            lock.setLastOperator("admin");
            lock.setLevelId(1);
            lock.setStatDate(DateUtils.truncate(now, Calendar.DATE));
            lock.setWithdrawTotal(BigDecimal.ZERO);
            lock.setWithdrawTimes(0);
            lock.setRechargeTotal(BigDecimal.ZERO);
            lock.setRechargeTimes(0);
            lock.setBetTotal(BigDecimal.ZERO);
            fundUserLevelLockBusiness.updateByPrimaryKeySelective(lock);
        }
        return RPCResponseUtils.buildSuccessRpcResponse(true);
    }

    @Override
    public RPCResponse<Boolean> doUserLevelLock(List<String> userNames, Integer levelId) {
        if (userNames.size() > 10000)
            return RPCResponseUtils.buildSuccessRpcResponse(false);
        try {
            GlFundUserlevel level = fundUserlevelBusiness.findById(levelId);
            if (ObjectUtils.isEmpty(level)) {
                return RPCResponseUtils.buildSuccessRpcResponse(false);
            }

            StringBuffer failMessage = new StringBuffer();
            int failNum = 0;
            List<GlUserDO> users = new ArrayList<>();
            List<GlUserDO> userList = Lists.newArrayList();
            if (!CollectionUtils.isEmpty(userNames)) {
                userList = RPCResponseUtils.getData(userService.findByUsernames(userNames));
            }

            for (GlUserDO glUser : userList) {
                if (!level.getLevelType().equals(glUser.getUserType())) {
                    failMessage.append(glUser + "用户类型与层级类型不匹配; ");
                    failNum++;
                    continue;
                }
                users.add(glUser);
            }
            // 循环匹配用户名,找出不存在的用户
            if (!CollectionUtils.isEmpty(userNames)) {
                for (String u : userNames) {
                    if (userList.stream().noneMatch(t -> u.equals(t.getUsername()))) {
                        failMessage.append(u + " 不存在; ");
                        failNum++;
                    }
                }
            }
            FundUserLeveLockDto lockDto = new FundUserLeveLockDto();
            lockDto.setUsers(users);
            lockDto.setLevel(level);
            lockDto.setAdmin("System");
            fundUserLevelLockBusiness.doUserLock(lockDto);
        } catch (Exception e) {
            return RPCResponseUtils.buildSuccessRpcResponse(false);
        }
        return RPCResponseUtils.buildSuccessRpcResponse(true);
    }
}
