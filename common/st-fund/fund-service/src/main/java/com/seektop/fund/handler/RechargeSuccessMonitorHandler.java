package com.seektop.fund.handler;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.user.UserOperateType;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.FirstRechargeLevelConfigBusiness;
import com.seektop.fund.business.GlFundUserLevelLockBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.common.UserFundUtils;
import com.seektop.fund.controller.backend.param.recharge.FirstRechargeLevelConfigCreateParamDO;
import com.seektop.fund.controller.backend.param.recharge.FirstRechargeLevelConfigEditParamDO;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.model.FirstRechargeLevelConfig;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.report.user.UserSynch;
import com.seektop.user.dto.GlUserManageDO;
import com.seektop.user.service.UserManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargeSuccessMonitorHandler {

    private final RedisService redisService;
    private final ReportService reportService;
    private final UserFundUtils userFundUtils;

    private final GlRechargeBusiness glRechargeBusiness;
    private final GlFundUserlevelBusiness glFundUserlevelBusiness;
    private final GlFundUserLevelLockBusiness glFundUserLevelLockBusiness;
    private final FirstRechargeLevelConfigBusiness firstRechargeLevelConfigBusiness;

    @DubboReference(retries = 1, timeout = 10000)
    private UserManageService userManageService;

    /**
     * 配置列表
     *
     * @param adminDO
     * @return
     */
    public Result configList(GlAdminDO adminDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(firstRechargeLevelConfigBusiness.findAll()).build();
        } catch (Exception ex) {
            log.error("用户存款成功自动调整层级配置获取列表时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    /**
     * 删除配置
     *
     * @param adminDO
     * @param levelId
     * @return
     */
    public Result submitDelete(GlAdminDO adminDO, Integer levelId) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(levelId)) {
            return newBuilder.paramError().build();
        }
        try {
            FirstRechargeLevelConfig levelConfig = firstRechargeLevelConfigBusiness.findById(levelId);
            if (ObjectUtils.isEmpty(levelConfig)) {
                return newBuilder.fail().setMessage("被调整层级ID的配置不存在").build();
            }
            firstRechargeLevelConfigBusiness.deleteById(levelId);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("用户存款成功自动调整层级配置删除时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    /**
     * 更新状态
     *
     * @param adminDO
     * @param levelId
     * @return
     */
    public Result submitUpdateStatus(GlAdminDO adminDO, Integer levelId) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(levelId)) {
            return newBuilder.paramError().build();
        }
        try {
            FirstRechargeLevelConfig levelConfig = firstRechargeLevelConfigBusiness.findById(levelId);
            if (ObjectUtils.isEmpty(levelConfig)) {
                return newBuilder.fail().setMessage("被调整层级ID的配置不存在").build();
            }
            if (levelConfig.getStatus() == 0) {
                firstRechargeLevelConfigBusiness.confirmUpdate(adminDO, levelId, (short) 1);
            } else if (levelConfig.getStatus() == 1) {
                firstRechargeLevelConfigBusiness.confirmUpdate(adminDO, levelId, (short) 0);
            }
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("用户存款成功自动调整层级配置更新状态时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    /**
     * 保存配置
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result submitCreateConfig(GlAdminDO adminDO, FirstRechargeLevelConfigCreateParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            // 检查被调整层级是否存在
            GlFundUserlevel configUserLevel = glFundUserlevelBusiness.findById(paramDO.getLevelId());
            if (ObjectUtils.isEmpty(configUserLevel)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.FUND_LEVEL_ID_NOT_EXIST).parse(paramDO.getLanguage())).build();
            }
            // 检查目标层级是否存在
            GlFundUserlevel targetUserLevel = glFundUserlevelBusiness.findById(paramDO.getTargetLevelId());
            if (ObjectUtils.isEmpty(targetUserLevel)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.FUND_LEVEL_TARGER_ID_NOT_EXIST).parse(paramDO.getLanguage())).build();
            }
            firstRechargeLevelConfigBusiness.confirmCreate(adminDO, paramDO.getSuccessTimes(), configUserLevel, targetUserLevel);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("用户存款成功自动调整层级配置保存时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    /**
     * 编辑配置
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result submitEditConfig(GlAdminDO adminDO, FirstRechargeLevelConfigEditParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            // 检查被调整层级是否存在
            GlFundUserlevel configUserLevel = glFundUserlevelBusiness.findById(paramDO.getLevelId());
            if (ObjectUtils.isEmpty(configUserLevel)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.FUND_LEVEL_ID_NOT_EXIST).parse(paramDO.getLanguage())).build();
            }
            // 检查目标层级是否存在
            GlFundUserlevel targetUserLevel = glFundUserlevelBusiness.findById(paramDO.getTargetLevelId());
            if (ObjectUtils.isEmpty(targetUserLevel)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.FUND_LEVEL_TARGER_ID_NOT_EXIST).parse(paramDO.getLanguage())).build();
            }
            firstRechargeLevelConfigBusiness.confirmUpdate(adminDO, paramDO.getSuccessTimes(), configUserLevel, targetUserLevel);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("用户存款成功自动调整层级配置编辑时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    public void checkChangeFundLevel(JSONObject dataObj) throws GlobalException {
        try {
            // 检查订单号是否存在
            String orderId = dataObj.getString("uuid");
            if (StringUtils.isEmpty(orderId)) {
                log.error("充值成功自动变更层级：没有订单号，无法处理");
                return;
            }
            // 获取用户ID
            Integer userId = dataObj.getInteger("uid");
            if (ObjectUtils.isEmpty(userId)) {
                log.error("充值成功自动变更层级：订单号{}，没有用户ID，无法处理", orderId);
                return;
            }
            GlUserDO userDO = redisService.get(KeyConstant.USER.DETAIL_CACHE + userId, GlUserDO.class);
            if (ObjectUtils.isEmpty(userDO)) {
                log.error("充值成功自动变更层级：订单号{}，用户ID{}通过Redis查询到的信息为空，无法处理", orderId, userId);
                return;
            }
            // 检查1000事件数据内容是否存在
            if (dataObj.containsKey("1000") == false) {
                log.error("充值成功自动变更层级：订单号{}，没有充值1000的详情数据，无法处理", orderId);
                return;
            }
            JSONObject rechargeDataObj = dataObj.getJSONObject("1000");
            // 检查状态
            if (rechargeDataObj.containsKey("status") == false) {
                log.error("充值成功自动变更层级：订单号{}，状态不存在，无法处理", orderId);
                return;
            }
            if (FundConstant.RechargeStatus.SUCCESS != rechargeDataObj.getInteger("status")) {
                log.info("充值成功自动变更层级：订单{}当前状态是{}，不是成功状态的订单，不进行处理", orderId, rechargeDataObj.getIntValue("status"));
                return;
            }
            // 获取当前用户的层级
            FundUserLevelDO currentUserLevel = userFundUtils.getFundUserLevel(userId);
            if (ObjectUtils.isEmpty(currentUserLevel)) {
                log.error("充值成功自动变更层级：订单号{}，用户{}通过Redis查询到的当前财务层级信息为空，无法进行层级变更检查", orderId, userId);
                return;
            }
            // 检查用户是否需要进行层级变更
            FirstRechargeLevelConfig rechargeSuccessLevelConfig = firstRechargeLevelConfigBusiness.findById(currentUserLevel.getLevelId());
            if (ObjectUtils.isEmpty(rechargeSuccessLevelConfig) || rechargeSuccessLevelConfig.getStatus() == 1) {
                log.error("充值成功自动变更层级：订单号{}，用户{}当前层级{}-{}未配置变更方案，不进行处理", orderId, userId, currentUserLevel.getLevelId(), currentUserLevel.getLevelName());
                return;
            }
            // 检查目标变更层级是否正常
            GlFundUserlevel targetUserLevel = glFundUserlevelBusiness.findById(rechargeSuccessLevelConfig.getTargetLevelId());
            if (ObjectUtils.isEmpty(targetUserLevel)) {
                log.error("充值成功自动变更层级：订单号{}，用户{}，目标层级{}通过数据库查询到的信息为空，不进行处理", orderId, userId, rechargeSuccessLevelConfig.getTargetLevelId());
                return;
            }
            // 获取用户充值成功次数
            Integer successTimes = glRechargeBusiness.countUserSuccessTimes(userDO.getId());
            if (successTimes < rechargeSuccessLevelConfig.getRechargeSuccessTimes()) {
                log.error("充值成功自动变更层级：订单号{}，用户{}，用户充值成功{}次，层级调整配置需求{}次，不进行处理", orderId, userId, rechargeSuccessLevelConfig.getTargetLevelId(), successTimes, rechargeSuccessLevelConfig.getRechargeSuccessTimes());
                return;
            }
            // 更新用户的财务层级
            glFundUserLevelLockBusiness.updateUserLevel(userDO.getId(), targetUserLevel.getLevelId());
            // 保存用户层级变更记录
            userManageService.saveManage(getGlUserManage(userDO, currentUserLevel.getLevelName(), targetUserLevel.getName(), successTimes));
            // 同步用户信息到ES
            userSynch(userDO.getId(), targetUserLevel.getLevelId(), targetUserLevel.getName());
            // 更新当前用户的层级缓存
            userFundUtils.setCache(userDO.getId(), targetUserLevel.getLevelId(), targetUserLevel.getName(), targetUserLevel.getLevelType());
            log.info("充值成功自动变更层级：用户{}从 {} 变更到 {} 成功", userDO.getUsername(), currentUserLevel.getLevelName(), targetUserLevel.getName());
        } catch (Exception ex) {
            throw new GlobalException("充值成功自动变更用户财务层级发生异常", ex);
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

    protected GlUserManageDO getGlUserManage(GlUserDO userDO, String beforeLevelName, String afterLevelName, Integer successTimes) {
        GlUserManageDO manage = new GlUserManageDO();
        manage.setUserId(userDO.getId());
        manage.setUsername(userDO.getUsername());
        manage.setUserType(userDO.getUserType());
        manage.setOptType(UserOperateType.LEVEL_MANUAL_CHANGE.getOptType());
        manage.setOptDesc(UserOperateType.LEVEL_MANUAL_CHANGE.getDesc());
        manage.setOptData("用户层级");
        manage.setOptBeforeData(beforeLevelName);
        manage.setOptAfterData(afterLevelName);
        manage.setRemark("充值成功" + successTimes + "次，自动变更层级");
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

}