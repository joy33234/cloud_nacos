package com.seektop.fund.handler;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.data.result.recharge.FirstRechargeDetailDO;
import com.seektop.data.service.UserService;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.user.UserOperateType;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserLevelLockBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.RechargeFailureLevelConfigBusiness;
import com.seektop.fund.common.UserFundUtils;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.param.recharge.RechargeFailureLevelConfigParamDO;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.RechargeFailureLevelConfig;
import com.seektop.report.user.UserSynch;
import com.seektop.user.dto.GlUserManageDO;
import com.seektop.user.service.UserManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargeFailureMonitorHandler {

    private final RedisService redisService;
    private final ReportService reportService;

    private final UserFundUtils userFundUtils;
    private final UserVipUtils userVipUtils;

    private final GlFundUserlevelBusiness glFundUserlevelBusiness;
    private final GlFundUserLevelLockBusiness glFundUserLevelLockBusiness;
    private final RechargeFailureLevelConfigBusiness rechargeFailureLevelConfigBusiness;

    @DubboReference(retries = 1, timeout = 10000)
    private UserService userService;
    @DubboReference(retries = 1, timeout = 10000)
    private UserManageService userManageService;

    public Result configList(GlAdminDO adminDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(rechargeFailureLevelConfigBusiness.findConfig()).build();
        } catch (Exception ex) {
            log.error("获取配置列表发生异常", ex);
            return newBuilder.fail().setMessage("获取配置列表发生异常").build();
        }
    }

    public Result submitRechargeFailureConfig(GlAdminDO adminDO, RechargeFailureLevelConfigParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            // 检查目标层级是否存在
            GlFundUserlevel fundUserLevel = glFundUserlevelBusiness.findById(paramDO.getTargetLevelId());
            if (ObjectUtils.isEmpty(fundUserLevel) || fundUserLevel.getLevelType() != 0) {
                return newBuilder.fail().setMessage("要变更的目标层级不存在").build();
            }
            // 循环保存配置
            List<Integer> configFundLevelIdArray = Arrays.asList(paramDO.getLevelIds().split(",")).stream().map(s -> Integer.valueOf(s.trim())).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(configFundLevelIdArray)) {
                return newBuilder.success().build();
            }
            for (Integer configFundLevelId : configFundLevelIdArray) {
                rechargeFailureLevelConfigBusiness.confirmUpdateConfig(
                        configFundLevelId,
                        fundUserLevel.getLevelId(),
                        fundUserLevel.getName(),
                        paramDO.getNewUserTimes(),
                        paramDO.getOldUserTimes(),
                        paramDO.getVips(),
                        adminDO
                );
            }
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("保存连续充值失败变更层级配置发生异常", ex);
            return newBuilder.fail().setMessage("保存连续充值失败变更层级配置发生错误").build();
        }
    }

    public Result submitRemoveConfig(GlAdminDO adminDO, String levelIds) {
        Result.Builder newBuilder = Result.newBuilder();
        if (StringUtils.isEmpty(levelIds)) {
            return newBuilder.paramError().build();
        }
        try {
            // 循环保存配置
            List<Integer> configFundLevelIdArray = Arrays.asList(levelIds.split(",")).stream().map(s -> Integer.valueOf(s.trim())).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(configFundLevelIdArray)) {
                return newBuilder.success().build();
            }
            for (Integer configFundLevelId : configFundLevelIdArray) {
                rechargeFailureLevelConfigBusiness.confirmRemoveConfig(
                        configFundLevelId,
                        adminDO
                );
            }
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("移除连续充值失败变更层级配置发生异常", ex);
            return newBuilder.fail().setMessage("移除连续充值失败变更层级配置发生错误").build();
        }
    }

    public Result getSwitch() {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(RedisTools.valueOperations().get("RECHARGE_FAILURE_MONITOR_SWITCH", Boolean.class)).build();
        } catch (Exception ex) {
            log.error("获取连续充值失败自动变更层级开关发生异常", ex);
            return newBuilder.fail().setMessage("获取自动变更层级开关错误").build();
        }
    }

    public Result setSwitch(Integer isOpen) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(isOpen)) {
            return newBuilder.paramError().build();
        }
        try {
            if (isOpen == 0) {
                RedisTools.valueOperations().set("RECHARGE_FAILURE_MONITOR_SWITCH", Boolean.FALSE);
            } else if (isOpen == 1) {
                RedisTools.valueOperations().set("RECHARGE_FAILURE_MONITOR_SWITCH", Boolean.TRUE);
            } else {
                return newBuilder.paramError().build();
            }
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("设置连续充值失败自动变更层级开关发生异常", ex);
            return newBuilder.fail().setMessage("设置自动变更层级开关错误").build();
        }
    }

    public void failureChangeFundLevel(JSONObject dataObj) throws GlobalException {
        Boolean isOpen = RedisTools.valueOperations().get("RECHARGE_FAILURE_MONITOR_SWITCH", Boolean.class);
        if (isOpen == false) {
            log.info("收到充值上报数据，但是当前连续充值失败监控开关没有开启");
            return;
        }
        // 检查订单号是否存在
        String orderId = dataObj.getString("uuid");
        if (StringUtils.isEmpty(orderId)) {
            log.error("收到的充值数据：没有订单号，无法处理");
            return;
        }
        // 获取用户ID
        Integer userId = dataObj.getInteger("uid");
        if (ObjectUtils.isEmpty(userId)) {
            log.error("收到的充值数据：没有用户ID，无法处理");
            return;
        }
        // 检查1000事件数据内容是否存在
        if (dataObj.containsKey("1000") == false) {
            log.error("收到的充值数据：没有充值1000的详情数据，无法处理");
            return;
        }
        JSONObject rechargeDataObj = dataObj.getJSONObject("1000");
        // 检查状态
        if (rechargeDataObj.containsKey("status") == false) {
            log.error("收到的充值数据：订单{}没有上报状态数据，无法处理", orderId);
            return;
        }
        Integer status = rechargeDataObj.getInteger("status");
        if (status == FundConstant.RechargeStatus.PENDING || status == FundConstant.RechargeStatus.REVIEW ) {
            log.info("收到的充值数据：订单{}当前状态是{}，不需要处理", orderId, rechargeDataObj.getIntValue("status"));
            return;
        }
        try {
            // 获取当前用户的层级
            FundUserLevelDO fundUserLevel = userFundUtils.getFundUserLevel(userId);
            if (ObjectUtils.isEmpty(fundUserLevel)) {
                log.error("用户{}通过Redis查询到的当前财务层级信息为空，无法进行层级变更检查", userId);
                return;
            }
            // 获取充值失败层级变更的配置
            RechargeFailureLevelConfig levelChangeConfig = rechargeFailureLevelConfigBusiness.findById(fundUserLevel.getLevelId());
            if (ObjectUtils.isEmpty(levelChangeConfig) || levelChangeConfig.getStatus() == 1) {
                log.info("用户{}当前层级{}的变更配置不存在，无需变更层级", userId, fundUserLevel.getLevelId());
                return;
            }
            // 检查用户当前层级是否已经是变更的目标层级
            if (fundUserLevel.getLevelId().equals(levelChangeConfig.getTargetLevelId())) {
                log.info("收到的充值数据：用户{}当前层级已经是目标层级，不需要处理");
                return;
            }
            // 检查用户当前的VIP层级是否符合条件
            UserVIPCache userVIPCache = userVipUtils.getUserVIPCache(userId);
            if (ObjectUtils.isEmpty(userVIPCache)) {
                log.error("用户{}通过Redis查询到的当前VIP信息为空，无法进行层级变更检查", userId);
                return;
            }
            if (StringUtils.hasText(levelChangeConfig.getVips())) {
                List<Integer> vipConfigs = Arrays.asList(levelChangeConfig.getVips().split(",")).stream().map(s -> Integer.valueOf(s.trim())).collect(Collectors.toList());
                if (vipConfigs.contains(userVIPCache.getVipLevel()) == false) {
                    log.error("用户{}当前的VIP等级是{}，不满足配置{}的条件，无需变更层级", userId, userVIPCache.getVipLevel(), levelChangeConfig.getVips());
                    return;
                }
            }
            String failureTimesKey = "USER_RECHARGE_FAILURE_TIMES_" + userId;
            Long failureTimes = RedisTools.valueOperations().get(failureTimesKey, Long.class);
            // 如果当前是成功，直接清除用户充值连续失败次数计算
            if (FundConstant.RechargeStatus.SUCCESS == status) {
                RedisTools.template().delete(failureTimesKey);
                log.info("用户{}在失败{}次后充值成功，清除已经累计的失败次数", userId, failureTimes);
                return;
            }
            // 如果当前是失败，累加用户失败次数
            failureTimes = RedisTools.valueOperations().increment(failureTimesKey, 1);
            Long allowFailureTimes = getUserAllowFailureTimes(userId, levelChangeConfig);
            log.info("用户{}当前第{}次充值失败，允许的最大充值失败次数是{}", userId, failureTimes, allowFailureTimes);
            if (failureTimes >= allowFailureTimes) {
                // 变更用户层级
                updateUserLevel(userId, levelChangeConfig.getTargetLevelId(), fundUserLevel.getLevelName(), allowFailureTimes);
                // 删除用户充值失败的次数
                RedisTools.template().delete(failureTimesKey);
                return;
            }
        } catch (Exception ex) {
            throw new GlobalException("充值失败监控自动变更用户财务层级发生异常", ex);
        }
    }

    @Transactional(rollbackFor = GlobalException.class)
    protected void updateUserLevel(Integer userId, Integer targetLevelId, String beforeLevelName, Long allowFailureTimes) throws GlobalException {
        try {
            GlUserDO userDO = redisService.get(KeyConstant.USER.DETAIL_CACHE + userId, GlUserDO.class);
            if (ObjectUtils.isEmpty(userDO)) {
                log.error("用户{}通过Redis查询到的信息为空，无法进行层级变更", userId);
                return;
            }
            GlFundUserlevel fundUserLevel = glFundUserlevelBusiness.findById(targetLevelId);
            if (ObjectUtils.isEmpty(fundUserLevel)) {
                log.error("目标层级{}通过数据库查询到的信息为空，无法进行层级变更", userDO.getId());
                return;
            }
            glFundUserLevelLockBusiness.updateUserLevel(userDO.getId(), fundUserLevel.getLevelId());
            // 保存用户层级变更记录
            userManageService.saveManage(getGlUserManage(userDO, beforeLevelName, fundUserLevel.getName(), allowFailureTimes));
            // 同步用户信息到ES
            userSynch(userDO.getId(), fundUserLevel.getLevelId(), fundUserLevel.getName());
            userFundUtils.setCache(userDO.getId(), fundUserLevel.getLevelId(), fundUserLevel.getName(), fundUserLevel.getLevelType());
        } catch (Exception ex) {
            throw new GlobalException("更新用户财务层级发生异常", ex);
        }
    }

    protected void userSynch(Integer userId, Integer levelId, String levelName) {
        UserSynch userSynch = new UserSynch();
        userSynch.setId(userId);
        userSynch.setLevel_id(levelId);
        userSynch.setLevel_name(levelName);
        userSynch.setLevel_status(1);
        userSynch.setLast_update(DateUtils.format(new Date(), "yyyy-MM-dd'T'HH:mm:ss'.000Z'"));
        reportService.userSynch(userSynch);
    }

    protected GlUserManageDO getGlUserManage(GlUserDO userDO, String beforeLevelName, String afterLevelName, Long allowFailureTimes) {
        GlUserManageDO manage = new GlUserManageDO();
        manage.setUserId(userDO.getId());
        manage.setUsername(userDO.getUsername());
        manage.setUserType(userDO.getUserType());
        manage.setOptType(UserOperateType.LEVEL_MANUAL_CHANGE.getOptType());
        manage.setOptDesc(UserOperateType.LEVEL_MANUAL_CHANGE.getDesc());
        manage.setOptData("用户层级");
        manage.setOptBeforeData(beforeLevelName);
        manage.setOptAfterData(afterLevelName);
        manage.setRemark("连续充值失败" + allowFailureTimes + "次，自动变更层级");
        manage.setCreator("系统自动");
        manage.setCreateTime(new Date());
        manage.setStatus(3);
        manage.setFirstApprover("系统自动");
        manage.setFirstTime(new Date());
        manage.setFirstRemark("通过");
        manage.setSecondApprover(manage.getFirstApprover());
        manage.setSecondTime(manage.getFirstTime());
        manage.setSecondRemark(manage.getFirstRemark());
        manage.setLockStatus(0);
        return manage;
    }

    protected Long getUserAllowFailureTimes(Integer userId, RechargeFailureLevelConfig levelChangeConfig) {
        String key = "FLG_USER_HASH_RECHARGE_SUCCESS_" + userId;
        if (RedisTools.template().hasKey(key)) {
            log.info("用户{}有成功充值的记录，属于老用户，允许连续充值失败{}次", userId, levelChangeConfig.getOldUserTimes());
            return levelChangeConfig.getOldUserTimes().longValue();
        } else {
            // 查询数据中心，检查用户是否完成首存
            RPCResponse<FirstRechargeDetailDO> firstRechargeDetailRPCResponse = userService.firstRechargeDetail(userId);
            if (RPCResponseUtils.isFail(firstRechargeDetailRPCResponse)) {
                log.info("用户{}查询首存信息失败，当新用户处理，允许连续充值失败{}次", userId, levelChangeConfig.getNewUserTimes());
                return levelChangeConfig.getNewUserTimes().longValue();
            }
            FirstRechargeDetailDO firstRechargeDetailDO = firstRechargeDetailRPCResponse.getData();
            if (ObjectUtils.isEmpty(firstRechargeDetailDO)) {
                log.info("用户{}查询首存信息为空，当新用户处理，允许连续充值失败{}次", userId, levelChangeConfig.getNewUserTimes());
                return levelChangeConfig.getNewUserTimes().longValue();
            }
            if (ObjectUtils.isEmpty(firstRechargeDetailDO.getFirstTime())) {
                log.info("用户{}查询首存信息成功，没有充值成功记录，属于新用户，允许连续充值失败{}次", userId, levelChangeConfig.getNewUserTimes());
                return levelChangeConfig.getNewUserTimes().longValue();
            }
            log.info("用户{}查询首存信息成功，有充值成功的记录，属于老用户，允许连续充值失败{}次", userId, levelChangeConfig.getOldUserTimes());
            RedisTools.valueOperations().set(key, "1");
            return levelChangeConfig.getOldUserTimes().longValue();
        }
    }

}