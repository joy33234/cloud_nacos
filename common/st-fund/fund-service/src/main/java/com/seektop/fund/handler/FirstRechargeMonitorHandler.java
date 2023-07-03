package com.seektop.fund.handler;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.user.UserOperateType;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserLevelLockBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.common.FirstRechargeSuccessUserLevelChangeConfigAdapter;
import com.seektop.fund.common.UserFundUtils;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
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
public class FirstRechargeMonitorHandler {

    private final FirstRechargeSuccessUserLevelChangeConfigAdapter firstRechargeSuccessUserLevelChangeConfigAdapter;

    private final RedisService redisService;
    private final ReportService reportService;
    private final UserFundUtils userFundUtils;

    private final GlFundUserlevelBusiness glFundUserlevelBusiness;
    private final GlFundUserLevelLockBusiness glFundUserLevelLockBusiness;

    @DubboReference(retries = 1, timeout = 10000)
    private UserManageService userManageService;

    public void checkChangeFundLevel(JSONObject dataObj) throws GlobalException {
        try {
            if (firstRechargeSuccessUserLevelChangeConfigAdapter.isEnable() == false) {
                log.info("首存成功自动变更层级：当前功能未开启，不进行检测");
                return;
            }
            // 检查订单号是否存在
            String orderId = dataObj.getString("uuid");
            if (StringUtils.isEmpty(orderId)) {
                log.error("首存成功自动变更层级：没有订单号，无法处理");
                return;
            }
            // 获取用户ID
            Integer userId = dataObj.getInteger("uid");
            if (ObjectUtils.isEmpty(userId)) {
                log.error("首存成功自动变更层级：订单号{}，没有用户ID，无法处理", orderId);
                return;
            }
            GlUserDO userDO = redisService.get(KeyConstant.USER.DETAIL_CACHE + userId, GlUserDO.class);
            if (ObjectUtils.isEmpty(userDO)) {
                log.error("首存成功自动变更层级：订单号{}，用户ID{}通过Redis查询到的信息为空，无法处理", orderId, userId);
                return;
            }
            // 检查1000事件数据内容是否存在
            if (dataObj.containsKey("1000") == false) {
                log.error("首存成功自动变更层级：订单号{}，没有充值1000的详情数据，无法处理", orderId);
                return;
            }
            JSONObject rechargeDataObj = dataObj.getJSONObject("1000");
            // 检查状态和首存标记位
            if (rechargeDataObj.containsKey("status") == false || rechargeDataObj.containsKey("first") == false) {
                log.error("首存成功自动变更层级：订单号{}，状态或首存标记位不存在，无法处理", orderId);
                return;
            }
            if (FundConstant.RechargeStatus.SUCCESS != rechargeDataObj.getInteger("status")) {
                log.info("首存成功自动变更层级：订单{}当前状态是{}，不是成功状态的订单，不进行处理", orderId, rechargeDataObj.getIntValue("status"));
                return;
            }
            if (1 != rechargeDataObj.getIntValue("first")) {
                log.info("首存成功自动变更层级：订单{}当前首存标记是{}，不是首存订单，不进行处理", orderId, rechargeDataObj.getIntValue("first"));
                return;
            }
            // 获取当前用户的层级
            FundUserLevelDO currentUserLevel = userFundUtils.getFundUserLevel(userId);
            if (ObjectUtils.isEmpty(currentUserLevel)) {
                log.error("首存成功自动变更层级：订单号{}，用户{}通过Redis查询到的当前财务层级信息为空，无法进行层级变更检查", orderId, userId);
                return;
            }
            // 检查当前层级是否需要进行检测
            if (firstRechargeSuccessUserLevelChangeConfigAdapter.checkSourceUserLevel(currentUserLevel.getLevelId()) == false) {
                log.error("首存成功自动变更层级：订单号{}，用户{}通过Redis查询到的当前财务层级信息为空，无法进行层级变更检查", orderId, userId);
                return;
            }
            Integer targetUserLevelId = firstRechargeSuccessUserLevelChangeConfigAdapter.getTargetUserLevelId();
            // 检查用户当前层级是否已经是变更的目标层级
            if (currentUserLevel.getLevelId().equals(targetUserLevelId)) {
                log.info("首存成功自动变更层级：订单号{}，用户{}当前层级已经是目标层级，不需要处理", orderId, userId);
                return;
            }
            GlFundUserlevel targetUserLevel = glFundUserlevelBusiness.findById(targetUserLevelId);
            if (ObjectUtils.isEmpty(targetUserLevel)) {
                log.error("首存成功自动变更层级：目标层级{}通过数据库查询到的信息为空，无法进行层级变更", userDO.getId());
                return;
            }
            // 更新用户的财务层级
            glFundUserLevelLockBusiness.updateUserLevel(userDO.getId(), targetUserLevel.getLevelId());
            // 保存用户层级变更记录
            userManageService.saveManage(getGlUserManage(userDO, currentUserLevel.getLevelName(), targetUserLevel.getName()));
            // 同步用户信息到ES
            userSynch(userDO.getId(), targetUserLevel.getLevelId(), targetUserLevel.getName());
            // 更新当前用户的层级缓存
            userFundUtils.setCache(userDO.getId(), targetUserLevel.getLevelId(), targetUserLevel.getName(), targetUserLevel.getLevelType());
            log.info("首存成功自动变更层级：用户{}从 {} 变更到 {} 成功", userDO.getUsername(), currentUserLevel.getLevelName(), targetUserLevel.getName());
        } catch (Exception ex) {
            throw new GlobalException("特定层级用户首存成自动变更用户财务层级发生异常", ex);
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

    protected GlUserManageDO getGlUserManage(GlUserDO userDO, String beforeLevelName, String afterLevelName) {
        GlUserManageDO manage = new GlUserManageDO();
        manage.setUserId(userDO.getId());
        manage.setUsername(userDO.getUsername());
        manage.setUserType(userDO.getUserType());
        manage.setOptType(UserOperateType.LEVEL_MANUAL_CHANGE.getOptType());
        manage.setOptDesc(UserOperateType.LEVEL_MANUAL_CHANGE.getDesc());
        manage.setOptData("用户层级");
        manage.setOptBeforeData(beforeLevelName);
        manage.setOptAfterData(afterLevelName);
        manage.setRemark("首存成功-自动变更层级");
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