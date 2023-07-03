package com.seektop.fund.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageInfo;
import com.seektop.activity.dto.result.C2cEasterEggActivityConfigDo;
import com.seektop.activity.service.GlActivityService;
import com.seektop.common.redis.RedisLock;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.fund.C2CEggStatusEnum;
import com.seektop.enumerate.fund.C2CEggTypeEnum;
import com.seektop.enumerate.push.Channel;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.c2c.C2CEggRecordBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.common.C2COrderUtils;
import com.seektop.fund.controller.backend.param.c2c.C2CEggOpenParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CEggRecordListParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CEggStopParamDO;
import com.seektop.fund.controller.forehead.result.C2CEggRecordResult;
import com.seektop.fund.controller.forehead.result.C2CEggResult;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.model.C2CEggRecord;
import com.seektop.fund.model.GlRecharge;
import com.seektop.fund.model.GlWithdraw;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class C2CEggRecordHandler {

    private final RedisLock redisLock;
    private final C2COrderUtils c2COrderUtils;

    private final GlRechargeBusiness glRechargeBusiness;
    private final GlWithdrawBusiness glWithdrawBusiness;
    private final C2CEggRecordBusiness c2CEggRecordBusiness;

    private final WithdrawHandler withdrawHandler;

    @DubboReference(timeout = 3000, retries = 1)
    private GlActivityService glActivityService;

    /**
     * 检查彩蛋活动是否开启
     *
     * @param typeEnum
     * @return
     */
    public Boolean isOpen(C2CEggTypeEnum typeEnum) {
        C2CEggRecord eggRecord = c2CEggRecordBusiness.getAvailableEggRecord(typeEnum);
        return ObjectUtils.isEmpty(eggRecord) ? false : true;
    }

    /**
     * 检查提现是否满足提现彩蛋活动要求
     *
     * @param withdrawOrderId
     * @return
     */
    public Boolean checkAccordWithdrawEggActivity(String withdrawOrderId) {
        GlWithdraw withdraw = glWithdrawBusiness.findById(withdrawOrderId);
        if (ObjectUtils.isEmpty(withdraw)) {
            return false;
        }
        Date withdrawCreateDate = withdraw.getCreateDate();
        // 判断条件一：与当前正在进行中的提现彩蛋进行时间判断
        C2CEggRecord eggRecord = c2CEggRecordBusiness.getAvailableEggRecord(C2CEggTypeEnum.WITHDRAW);
        if (eggRecord != null && withdrawCreateDate.after(eggRecord.getStartDate())) {
            return true;
        }
        // 判断条件二：与已经结束的提现彩蛋进行时间对比(提现时间大于活动开始时间 && 提现时间小于活动结束时间)
        return c2CEggRecordBusiness.isAccord(withdrawCreateDate, C2CEggTypeEnum.WITHDRAW);
    }

    /**
     * 检查充值是否满足充值彩蛋活动要求
     *
     * @param rechargeOrderId
     * @return
     */
    public Boolean checkAccordRechargeEggActivity(String rechargeOrderId) {
        GlRecharge recharge = glRechargeBusiness.findById(rechargeOrderId);
        if (ObjectUtils.isEmpty(recharge)) {
            return false;
        }
        Date rechargeCreateDate = recharge.getCreateDate();
        // 判断条件一：与当前正在进行中的充值彩蛋进行时间判断
        C2CEggRecord eggRecord = c2CEggRecordBusiness.getAvailableEggRecord(C2CEggTypeEnum.RECHARGE);
        if (eggRecord != null && rechargeCreateDate.after(eggRecord.getStartDate())) {
            return true;
        }
        // 判断条件二：与已经结束的充值彩蛋进行时间对比(充值时间大于活动开始时间 && 充值时间小于活动结束时间)
        return c2CEggRecordBusiness.isAccord(rechargeCreateDate, C2CEggTypeEnum.RECHARGE);
    }

    /**
     * 管理端：分页查询彩蛋记录
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result listRecord(GlAdminDO adminDO, C2CEggRecordListParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            PageInfo<C2CEggRecord> pageInfo = c2CEggRecordBusiness.findPage(adminDO, paramDO);
            return newBuilder.success().addData(pageInfo).build();
        } catch (Exception ex) {
            log.error("彩蛋开启记录查询发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    /**
     * 管理端：结束彩蛋
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result submitStop(GlAdminDO adminDO, C2CEggStopParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        String lockKey = "C2C_EGG_SUBMIT_CLOSE_REDIS_LOCK_KEY";
        try {
            log.info("[彩蛋活动结束]-用户{}结束彩蛋活动，参数是{}", adminDO.getUsername(), JSON.toJSONString(paramDO));
            redisLock.lock(lockKey, 20, 100, 195);
            // 检查是否存在
            C2CEggRecord eggRecord = c2CEggRecordBusiness.findById(paramDO.getRecordId());
            Result result = newBuilder.fail().setMessage("").build();
            if (ObjectUtils.isEmpty(eggRecord)) {
                result.setKeyConfig(FundLanguageMvcEnum.C2C_EGG_ID_NOT_EXIST);
                return result;
            }
            // 检查是否在运行中
            if (eggRecord.getStatus() != C2CEggStatusEnum.PROCESSING.getStatus()) {
                result.setKeyConfig(FundLanguageMvcEnum.C2C_EGG_NOT_RUNNING);
                return result;
            }
            c2CEggRecordBusiness.confirmFinished(eggRecord.getId(), adminDO.getUsername());
            // 推送彩蛋结束通知到客户端
            c2COrderUtils.pushEggToApp(eggRecord.getId(), Channel.C2C_EGG_END);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("彩蛋停止发生异常", ex);
            return newBuilder.fail().build();
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    /**
     * 管理端：开启彩蛋
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result submitOpen(GlAdminDO adminDO, C2CEggOpenParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        String lockKey = "C2C_EGG_SUBMIT_OPEN_REDIS_LOCK_KEY";
        try {
            Result result = newBuilder.fail().setMessage("").build();
            log.info("[彩蛋活动开启]-用户{}开启彩蛋活动，参数是{}", adminDO.getUsername(), JSON.toJSONString(paramDO));
            C2CEggTypeEnum typeEnum = C2CEggTypeEnum.getC2CEggType(paramDO.getEggType());
            if (ObjectUtils.isEmpty(typeEnum)) {
                return newBuilder.paramError().build();
            }
            redisLock.lock(lockKey, 20, 100, 195);
            // 检查是否存在已经开启的彩蛋活动
            C2CEggRecord eggRecord = c2CEggRecordBusiness.getAvailableEggRecord();
            if (eggRecord != null) {
                result.setKeyConfig(FundLanguageMvcEnum.C2C_EGG_IS_RUNNING);
                return result;
            }
            // 获取活动的配置内容
            RPCResponse<C2cEasterEggActivityConfigDo> eggActivityConfigRCPResponse = null;
            switch (typeEnum) {
                case RECHARGE:
                    eggActivityConfigRCPResponse = glActivityService.findC2cRechargeEasterEggConfig();
                break;
                case WITHDRAW:
                    eggActivityConfigRCPResponse = glActivityService.findC2cWithdrawEasterEggConfig();
                break;
            }
            if (ObjectUtils.isEmpty(eggActivityConfigRCPResponse)) {
                result.setKeyConfig(FundLanguageMvcEnum.C2C_EGG_IS_RUNNING);
                return result;
            }
            if (RPCResponseUtils.isFail(eggActivityConfigRCPResponse)) {
                result.setKeyConfig(FundLanguageMvcEnum.C2C_EGG_GET_INFO_STATUS_ERROR);
                return result;
            }
            C2cEasterEggActivityConfigDo configDO = eggActivityConfigRCPResponse.getData();
            if (ObjectUtils.isEmpty(configDO)) {
                result.setKeyConfig(FundLanguageMvcEnum.C2C_EGG_GET_INFO_EMPTY);
                return result;
            }
            JSONObject configObj = new JSONObject();
            configObj.put("awardRate", configDO.getPercentage());
            configObj.put("eggIconPath", configDO.getImage());
            Integer recordId = c2CEggRecordBusiness.confirmCreate(adminDO.getUsername(), typeEnum, paramDO.getDuration(), configObj);
            // 设置一个倒计时时间
            RedisTools.valueOperations().set(KeyConstant.FUND.C2C_EGG_RECORD_CACHE + recordId, recordId, paramDO.getDuration() * 60, TimeUnit.SECONDS);
            // 推送彩蛋开始通知到客户端
            c2COrderUtils.pushEggToApp(recordId, Channel.C2C_EGG_START);
            // 推送彩蛋开始通知到极光
            c2COrderUtils.eggJpush(typeEnum, configDO.getPercentage());
            // 异步上报彩蛋倒计时数据
            c2COrderUtils.c2cEggRecordReport(recordId, Long.valueOf(paramDO.getDuration() * 60 * 1000));
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("彩蛋开启发生异常", ex);
            return newBuilder.fail().build();
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    /**
     * 管理端：获取彩蛋信息
     *
     * @param adminDO
     * @return
     */
    public Result loadC2CEggResult(GlAdminDO adminDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            C2CEggRecordResult result = new C2CEggRecordResult();
            // 获取最后一次的时长
            C2CEggRecord lastEggRecord = c2CEggRecordBusiness.getLastC2CEggRecord();
            if (lastEggRecord != null) {
                result.setDefaultDuration(lastEggRecord.getDuration());
            }
            // 检查是否存在已经开启的彩蛋活动
            C2CEggRecord eggRecord = c2CEggRecordBusiness.getAvailableEggRecord();
            if (ObjectUtils.isEmpty(eggRecord)) {
                return newBuilder.success().addData(result).build();
            }
            if (eggRecord.getStatus() == C2CEggStatusEnum.FINISHED.getStatus()) {
                return newBuilder.success().addData(result).build();
            }
            result.setIsOpen(true);
            // 组装返回的彩蛋参数
            result.setRecordId(eggRecord.getId());
            result.setType(eggRecord.getType());
            result.setTtl(RedisTools.template().getExpire(KeyConstant.FUND.C2C_EGG_RECORD_CACHE + eggRecord.getId()));
            return newBuilder.success().addData(result).build();
        } catch (Exception ex) {
            log.error("管理后端获取彩蛋信息发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    /**
     * 会员端：获取彩蛋信息
     *
     * @param userDO
     * @return
     */
    public Result loadC2CEggResult(GlUserDO userDO,String coin) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            C2CEggResult result = new C2CEggResult();
            // 检查是否存在已经开启的彩蛋活动
            C2CEggRecord eggRecord = c2CEggRecordBusiness.getAvailableEggRecord();
            if (ObjectUtils.isEmpty(eggRecord)) {
                return newBuilder.success().addData(result).build();
            }
            if (eggRecord.getStatus() == C2CEggStatusEnum.FINISHED.getStatus()) {
                return newBuilder.success().addData(result).build();
            }
            Boolean hasC2CAuth = false;
            C2CEggTypeEnum typeEnum = C2CEggTypeEnum.getC2CEggType(eggRecord.getType());
            switch (typeEnum) {
                case WITHDRAW:
                    hasC2CAuth = withdrawHandler.setC2CWithdrawOpen(userDO, coin);
                break;
                case RECHARGE:
                    String rechargeAuth = glRechargeBusiness.validC2C(userDO);
                    hasC2CAuth = StringUtils.isEmpty(rechargeAuth) ? true : false;
                break;
            }
            if (hasC2CAuth == false) {
                return newBuilder.success().addData(result).build();
            }
            result.setIsOpen(true);
            // 组装返回的彩蛋参数
            result.setRecordId(eggRecord.getId());
            result.setType(eggRecord.getType());
            result.setTtl(RedisTools.template().getExpire(KeyConstant.FUND.C2C_EGG_RECORD_CACHE + eggRecord.getId()));
            JSONObject configObj = JSONObject.parseObject(eggRecord.getConfig());
            if (configObj != null && configObj.containsKey("awardRate")) {
                result.setAwardRate(configObj.getBigDecimal("awardRate"));
            }
            if (configObj != null && configObj.containsKey("eggIconPath")) {
                result.setEggIconPath(configObj.getString("eggIconPath"));
            }
            return newBuilder.success().addData(result).build();
        } catch (Exception ex) {
            log.error("会员端获取彩蛋信息发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    public void submitStop(Integer recordId) throws GlobalException {
        String lockKey = "C2C_EGG_SUBMIT_CLOSE_REDIS_LOCK_KEY";
        try {
            log.info("[彩蛋活动结束]-记录ID是{}", recordId);
            redisLock.lock(lockKey, 20, 100, 195);
            // 检查是否存在
            C2CEggRecord eggRecord = c2CEggRecordBusiness.findById(recordId);
            if (ObjectUtils.isEmpty(eggRecord)) {
                return;
            }
            // 检查是否在运行中
            if (eggRecord.getStatus() != C2CEggStatusEnum.PROCESSING.getStatus()) {
                return;
            }
            c2CEggRecordBusiness.confirmFinished(eggRecord.getId(), "系统自动");
            // 推送彩蛋结束通知到客户端
            c2COrderUtils.pushEggToApp(eggRecord.getId(), Channel.C2C_EGG_END);
        } catch (Exception ex) {
            throw new GlobalException("彩蛋倒计时结束处理时发生异常", ex);
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

}