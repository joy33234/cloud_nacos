package com.seektop.fund.handler;

import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.data.param.betting.FindBettingCommParamDO;
import com.seektop.data.param.recharge.UserLevelQueryDto;
import com.seektop.data.result.recharge.RechargeAmountValidAmount;
import com.seektop.data.service.BettingService;
import com.seektop.data.service.RechargeService;
import com.seektop.data.service.UserService;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.user.UserOperateType;
import com.seektop.fund.business.GlFundUserLevelLockBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.RechargeBettingLevelConfigBusiness;
import com.seektop.fund.common.UserFundUtils;
import com.seektop.fund.controller.backend.param.recharge.RechargeBettingLevelConfigCreateParamDO;
import com.seektop.fund.controller.backend.param.recharge.RechargeBettingLevelConfigEditParamDO;
import com.seektop.fund.controller.backend.param.recharge.RechargeBettingLevelConfigListParamDO;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.RechargeBettingLevelConfig;
import com.seektop.report.user.UserSynch;
import com.seektop.user.dto.GlUserManageDO;
import com.seektop.user.service.UserManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargeBettingHandler {

    private final RedisService redisService;
    private final ReportService reportService;
    private final UserFundUtils userFundUtils;

    private final GlFundUserlevelBusiness glFundUserlevelBusiness;
    private final GlFundUserLevelLockBusiness glFundUserLevelLockBusiness;
    private final RechargeBettingLevelConfigBusiness rechargeBettingLevelConfigBusiness;

    @DubboReference(retries = 1, timeout = 5000)
    private RechargeService rechargeService;
    @DubboReference(retries = 1, timeout = 10000)
    private UserService userService;
    @DubboReference(retries = 1, timeout = 10000)
    private UserManageService userManageService;
    @DubboReference(retries = 1, timeout = 5000)
    private BettingService bettingService;

    @Async
    public void executeConfigCheck(Date runningDate, Integer size) {
        // 获取有效的配置
        List<RechargeBettingLevelConfig> configList = rechargeBettingLevelConfigBusiness.findAvailableConfig();
        if (CollectionUtils.isEmpty(configList)) {
            log.info("执行按流水的层级变更检查：当前没有启用的配置，不进行检查");
            return;
        }
        for (RechargeBettingLevelConfig config : configList) {
            try {
                Pair<Date, Date> datePair = getExecuteDate(runningDate, config.getDays());
                log.info("执行按流水的层级变更检查：准备检查的被调整层级{}-{}，天数{}，充值金额{}，流水倍数{}，目标层级{}-{}，时间段{}-{}", config.getLevelId(), config.getLevelName(), config.getDays(), config.getRechargeAmount(), config.getBettingMultiple(), config.getTargetLevelId(), config.getTargetLevelName(), DateUtils.format(datePair.getFirst(), DateUtils.YYYY_MM_DD_HH_MM_SS), DateUtils.format(datePair.getSecond(), DateUtils.YYYY_MM_DD_HH_MM_SS));
                // 查询当前层级下充值用户(用户ID，充值金额，有效投注)
                UserLevelQueryDto queryDto = new UserLevelQueryDto();
                queryDto.setLevelId(config.getLevelId());
                queryDto.setUserSize(size);
                queryDto.setStime(datePair.getFirst().getTime());
                queryDto.setEtime(datePair.getSecond().getTime());
                RPCResponse<List<RechargeAmountValidAmount>> rpcResponse = rechargeService.rechargeAmountValidAmount(queryDto);
                if (RPCResponseUtils.isFail(rpcResponse)) {
                    log.error("执行按流水的层级变更检查：查询被调整层级{}-{}下的用户充值失败", config.getLevelId(), config.getLevelName());
                    continue;
                }
                List<RechargeAmountValidAmount> rechargeAmountValidAmountList = rpcResponse.getData();
                if (CollectionUtils.isEmpty(rechargeAmountValidAmountList)) {
                    log.info("执行按流水的层级变更检查：查询被调整层级{}-{}下没有充值的用户", config.getLevelId(), config.getLevelName());
                    continue;
                }
                // 循环检查用户的层级变更
                for (RechargeAmountValidAmount rechargeAmountValidAmount : rechargeAmountValidAmountList) {
                    Integer userId = rechargeAmountValidAmount.getUid();
                    BigDecimal rechargeAmount = rechargeAmountValidAmount.getRechargeAmount();
                    // 检查充值金额是否满足要求(充值金额必须大于等于配置的充值金额)
                    if (rechargeAmount.compareTo(config.getRechargeAmount()) == -1) {
                        log.info("执行按流水的层级变更检查：被调整层级{}-{}下的用户{} 充值金额{} 不满足充值金额{}的要求，不进行检查", config.getLevelId(), config.getLevelName(), userId, rechargeAmount, config.getRechargeAmount());
                        continue;
                    }
                    // 查询当前用户当前时间段的有效流水金额
                    FindBettingCommParamDO findBettingCommParamDO = new FindBettingCommParamDO();
                    findBettingCommParamDO.setUserId(userId);
                    findBettingCommParamDO.setStartTime(datePair.getFirst().getTime());
                    findBettingCommParamDO.setEndTime(datePair.getSecond().getTime());
                    RPCResponse<BigDecimal> bettingAmountRPCResponse = bettingService.sumBettingEffectiveAmountForWithdraw(findBettingCommParamDO);
                    if (RPCResponseUtils.isFail(bettingAmountRPCResponse)) {
                        log.info("执行按流水的层级变更检查：查询用户{}有效流水失败", userId);
                        continue;
                    }
                    BigDecimal bettingAmount = bettingAmountRPCResponse.getData();
                    log.info("用户{}在{} ~ {}的有效投注金额是{}", userId, DateUtils.format(datePair.getFirst(), DateUtils.YYYY_MM_DD_HH_MM_SS), DateUtils.format(datePair.getSecond(), DateUtils.YYYY_MM_DD_HH_MM_SS), bettingAmount);
                    BigDecimal configBettingAmount = rechargeAmount.multiply(config.getBettingMultiple());
                    // 检查有效投注金额是否满足要求(有效投注金额必须大于等于配置的有效投注金额)
                    if (bettingAmount.compareTo(configBettingAmount) == -1) {
                        log.info("执行按流水的层级变更检查：被调整层级{}-{}下的用户{} 充值金额{} 有效投注金额{}，不满足流水倍数{}-{}的要求，不进行检查", config.getLevelId(), config.getLevelName(), userId, rechargeAmount, bettingAmount, config.getBettingMultiple(), configBettingAmount);
                        continue;
                    }
                    // 检查用户当前的层级是否已经是目标层级
                    FundUserLevelDO fundUserLevel = userFundUtils.getFundUserLevel(userId);
                    if (ObjectUtils.isEmpty(fundUserLevel)) {
                        log.error("用户{}通过Redis查询到的当前财务层级信息为空，无法进行层级变更检查", userId);
                        continue;
                    }
                    if (fundUserLevel.getLevelId().equals(config.getTargetLevelId())) {
                        log.info("收到的充值数据：用户{}当前层级已经是目标层级，不需要处理", userId);
                        continue;
                    }
                    // 查询当前用户信息
                    GlUserDO userDO = redisService.get(KeyConstant.USER.DETAIL_CACHE + userId, GlUserDO.class);
                    if (ObjectUtils.isEmpty(userDO)) {
                        log.error("用户{}通过Redis查询到的信息为空，无法进行层级变更", userId);
                        return;
                    }
                    GlFundUserlevel targetUserLevel = glFundUserlevelBusiness.findById(config.getTargetLevelId());
                    glFundUserLevelLockBusiness.updateUserLevel(userId, config.getTargetLevelId());
                    // 保存用户层级变更记录
                    userManageService.saveManage(getGlUserManage(userDO, fundUserLevel.getLevelName(), targetUserLevel.getName(), config.getDays(), config.getRechargeAmount(), config.getBettingMultiple()));
                    // 同步用户信息到ES
                    userSynch(userDO.getId(), targetUserLevel.getLevelId(), targetUserLevel.getName());
                    // 更新用户的层级缓存
                    userFundUtils.setCache(userDO.getId(), targetUserLevel.getLevelId(), targetUserLevel.getName(), targetUserLevel.getLevelType());
                    log.info("用户{}成功从层级{}变成{}", userDO.getUsername(), fundUserLevel.getLevelName(), targetUserLevel.getName());
                }
            } catch (Exception ex) {
                log.error("执行按流水的层级变更检查时发生异常", ex);
            }
        }
    }

    public Result configList(GlAdminDO adminDO, RechargeBettingLevelConfigListParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(rechargeBettingLevelConfigBusiness.findRechargeBettingLevelConfig(paramDO)).build();
        } catch (Exception ex) {
            log.error("获取配置列表发生异常", ex);
            return newBuilder.fail().setMessage("获取配置列表发生异常").build();
        }
    }

    public Result submitCreate(GlAdminDO adminDO, RechargeBettingLevelConfigCreateParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            // 检查被调整层级是否存在
            GlFundUserlevel configUserLevel = glFundUserlevelBusiness.findById(paramDO.getLevelId());
            if (ObjectUtils.isEmpty(configUserLevel)) {
                return newBuilder.fail().setMessage("被调整层级ID不存在").build();
            }
            // 检查目标层级是否存在
            GlFundUserlevel targetUserLevel = glFundUserlevelBusiness.findById(paramDO.getTargetLevelId());
            if (ObjectUtils.isEmpty(targetUserLevel)) {
                return newBuilder.fail().setMessage("目标层级ID不存在").build();
            }
            rechargeBettingLevelConfigBusiness.confirmCreate(adminDO, paramDO, configUserLevel, targetUserLevel);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("保存配置时发生异常", ex);
            return newBuilder.fail().setMessage("保存配置时发生异常").build();
        }
    }

    public Result submitEdit(GlAdminDO adminDO, RechargeBettingLevelConfigEditParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            RechargeBettingLevelConfig config = rechargeBettingLevelConfigBusiness.findById(paramDO.getRecordId());
            if (ObjectUtils.isEmpty(config)) {
                return newBuilder.fail().setMessage("配置记录ID不存在").build();
            }
            // 检查被调整层级是否存在
            GlFundUserlevel configUserLevel = glFundUserlevelBusiness.findById(paramDO.getLevelId());
            if (ObjectUtils.isEmpty(configUserLevel)) {
                return newBuilder.fail().setMessage("被调整层级ID不存在").build();
            }
            // 检查目标层级是否存在
            GlFundUserlevel targetUserLevel = glFundUserlevelBusiness.findById(paramDO.getTargetLevelId());
            if (ObjectUtils.isEmpty(targetUserLevel)) {
                return newBuilder.fail().setMessage("目标层级ID不存在").build();
            }
            rechargeBettingLevelConfigBusiness.confirmEdit(adminDO, paramDO, configUserLevel, targetUserLevel);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("编辑配置时发生异常", ex);
            return newBuilder.fail().setMessage("编辑配置时发生异常").build();
        }
    }

    public Result submitOpen(GlAdminDO adminDO, Long recordId) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(recordId)) {
            return newBuilder.paramError().build();
        }
        try {
            RechargeBettingLevelConfig config = rechargeBettingLevelConfigBusiness.findById(recordId);
            if (ObjectUtils.isEmpty(config)) {
                return newBuilder.fail().setMessage("配置记录ID不存在").build();
            }
            if (1 != config.getStatus()) {
                return newBuilder.fail().setMessage("配置的当前状态不能开启").build();
            }
            rechargeBettingLevelConfigBusiness.confirmOpen(adminDO, config.getId());
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("开启配置时发生异常", ex);
            return newBuilder.fail().setMessage("开启配置时发生异常").build();
        }
    }

    public Result submitClose(GlAdminDO adminDO, Long recordId) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(recordId)) {
            return newBuilder.paramError().build();
        }
        try {
            RechargeBettingLevelConfig config = rechargeBettingLevelConfigBusiness.findById(recordId);
            if (ObjectUtils.isEmpty(config)) {
                return newBuilder.fail().setMessage("配置记录ID不存在").build();
            }
            if (0 != config.getStatus()) {
                return newBuilder.fail().setMessage("配置的当前状态不能关闭").build();
            }
            rechargeBettingLevelConfigBusiness.confirmClose(adminDO, config.getId());
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("关闭配置时发生异常", ex);
            return newBuilder.fail().setMessage("关闭配置时发生异常").build();
        }
    }

    public Result submitDelete(GlAdminDO adminDO, Long recordId) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(recordId)) {
            return newBuilder.paramError().build();
        }
        try {
            RechargeBettingLevelConfig config = rechargeBettingLevelConfigBusiness.findById(recordId);
            if (ObjectUtils.isEmpty(config)) {
                return newBuilder.fail().setMessage("配置记录ID不存在").build();
            }
            rechargeBettingLevelConfigBusiness.confirmDelete(adminDO, config.getId());
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("删除配置时发生异常", ex);
            return newBuilder.fail().setMessage("删除配置时发生异常").build();
        }
    }

    protected Pair<Date, Date> getExecuteDate(Date runningDate, Integer days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(runningDate);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        // 结束时间
        Date endDate = calendar.getTime();
        // 开始时间
        calendar.add(Calendar.DAY_OF_MONTH, -days);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startDate = calendar.getTime();
        return Pair.of(startDate, endDate);
    }

    protected GlUserManageDO getGlUserManage(GlUserDO userDO, String beforeLevelName, String afterLevelName, Integer days, BigDecimal rechargeAmount, BigDecimal bettingMultiple) {
        GlUserManageDO manage = new GlUserManageDO();
        manage.setUserId(userDO.getId());
        manage.setUsername(userDO.getUsername());
        manage.setUserType(userDO.getUserType());
        manage.setOptType(UserOperateType.LEVEL_MANUAL_CHANGE.getOptType());
        manage.setOptDesc(UserOperateType.LEVEL_MANUAL_CHANGE.getDesc());
        manage.setOptData("用户层级");
        manage.setOptBeforeData(beforeLevelName);
        manage.setOptAfterData(afterLevelName);
        manage.setRemark("满足：" + days + "天，充值" + rechargeAmount + "且有效流水" + bettingMultiple + "倍，自动变更层级");
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

    protected void userSynch(Integer userId, Integer levelId, String levelName) {
        UserSynch userSynch = new UserSynch();
        userSynch.setId(userId);
        userSynch.setLevel_id(levelId);
        userSynch.setLevel_name(levelName);
        userSynch.setLevel_status(1);
        userSynch.setLast_update(DateUtils.format(new Date(), "yyyy-MM-dd'T'HH:mm:ss'.000Z'"));
        reportService.userSynch(userSynch);
    }

}