package com.seektop.fund.handler;

import com.github.pagehelper.PageInfo;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisLock;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.Result;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.fund.C2CMatchLogTypeEnum;
import com.seektop.enumerate.fund.C2CWithdrawOrderStatusEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundC2CRechargeBusiness;
import com.seektop.fund.business.GlFundC2CWithdrawBusiness;
import com.seektop.fund.business.c2c.C2CMatchLogBusiness;
import com.seektop.fund.business.c2c.C2CWithdrawOrderPoolBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.common.C2COrderDetailResult;
import com.seektop.fund.controller.backend.param.c2c.C2CMatchLogListParamDO;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.model.C2CMatchLog;
import com.seektop.fund.model.C2CWithdrawOrderPool;
import com.seektop.fund.model.GlRecharge;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.report.fund.C2CWithdrawReceiveAlertReport;
import com.seektop.report.fund.C2CWithdrawReceiveTimeoutReport;
import com.seektop.system.dto.param.*;
import com.seektop.system.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class C2COrderHandler {

    private final RedisLock redisLock;
    private final ReportService reportService;

    private final GlFundC2CRechargeBusiness glFundC2CRechargeBusiness;
    private final GlFundC2CWithdrawBusiness glFundC2CWithdrawBusiness;
    private final GlRechargeBusiness glRechargeBusiness;
    private final GlWithdrawBusiness glWithdrawBusiness;
    private final C2CMatchLogBusiness c2CMatchLogBusiness;
    private final C2CWithdrawOrderPoolBusiness c2CWithdrawOrderPoolBusiness;

    private final C2COrderTransactionalHandler c2COrderTransactionalHandler;

    @DubboReference(timeout = 5000, retries = 1)
    private NoticeService noticeService;

    private final static List<Short> confirmReceiveStatusArray = Arrays.asList(
            C2CWithdrawOrderStatusEnum.WAIT_PAYMENT.getStatus(),
            C2CWithdrawOrderStatusEnum.WAIT_CONFIRM_RECEIVE.getStatus()
    );

    /**
     * 撮合日志列表
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result matchLogList(GlAdminDO adminDO, C2CMatchLogListParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            C2CMatchLogTypeEnum logTypeEnum = C2CMatchLogTypeEnum.getMatchLogType(paramDO.getType());
            PageInfo<C2CMatchLog> pageInfo =  c2CMatchLogBusiness.findPage(paramDO.getOrderId(), logTypeEnum, paramDO.getPage(), paramDO.getSize());
            return newBuilder.success().addData(pageInfo).build();
        } catch (Exception ex) {
            log.error("获取订单{}的撮合日志记录发生异常", paramDO.getOrderId(), ex);
            return newBuilder.fail().build();
        }
    }

    /**
     * 充值订单确认付款
     *
     * @param orderId
     * @param userDO
     * @return
     */
    public Result submitConfirmPayment(String orderId, GlUserDO userDO, ParamBaseDO paramBaseDO) {
        Result.Builder newBuilder = Result.newBuilder();
        if (StringUtils.isEmpty(orderId)) {
            return newBuilder.paramError().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_EMPTY)
                    .withDefaultValue("充值订单为空").parse(paramBaseDO.getLanguage())).build();
        }
        String lockKey = "C2C_RECHARGE_CONFIRM_PAYMENT_REDIS_LOCK_KEY_" + orderId;
        try {
            redisLock.lock(lockKey, 20, 100, 195);
            // 检查充值订单的提交人是否当前用户
            GlRecharge glRecharge = glRechargeBusiness.findById(orderId);
            if (ObjectUtils.isEmpty(glRecharge)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_NOT_EXIST)
                        .withDefaultValue("充值订单不存在").parse(paramBaseDO.getLanguage())).build();
            }
            if (glRecharge.getUserId().equals(userDO.getId()) == false) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_NOT_EXIST)
                        .withDefaultValue("充值订单不存在").parse(paramBaseDO.getLanguage())).build();
            }
            // 检查撮合订单当前状态是否允许进行确认付款
            C2CWithdrawOrderPool matchOrder = c2CWithdrawOrderPoolBusiness.getByRechargeOrderId(orderId);
            if (ObjectUtils.isEmpty(matchOrder)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_NOT_EXIST)
                        .withDefaultValue("充值订单异常，请联系客服").parse(paramBaseDO.getLanguage())).build();
            }
            if (C2CWithdrawOrderStatusEnum.WAIT_PAYMENT.getStatus() != matchOrder.getStatus()) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_STATUS_ERROR)
                        .withDefaultValue("充值订单确认异常，请联系客服").parse(paramBaseDO.getLanguage())).build();
            }
            // 更新撮合订单状态和插入日志
            c2CWithdrawOrderPoolBusiness.confirmPayment(matchOrder, userDO);
            // 上报收款确认提醒和超时
            receiveAlertAndTimeoutReport(matchOrder.getWithdrawOrderId());
            // 发送付款提醒给提现用户
            sendPaymentAlertToWithdrawUser(matchOrder);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("充值订单{}确认付款时发生异常", orderId, ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_CONFIRM_ERROR).parse(paramBaseDO.getLanguage())).build();
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    /**
     * 提现订单确认收款
     *
     * @param orderId
     * @param userDO
     * @return
     */
    public Result submitConfirmReceive(String orderId, GlUserDO userDO, ParamBaseDO paramBaseDO) {
        Result.Builder newBuilder = Result.newBuilder();
        if (StringUtils.isEmpty(orderId)) {
            return newBuilder.paramError().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_ORDER_EMPTY)
                    .withDefaultValue("提现单号不能为空").parse(paramBaseDO.getLanguage())).build();
        }
        String lockKey = "C2C_WITHDRAW_CONFIRM_RECEIVE_REDIS_LOCK_KEY_" + orderId;
        try {
            redisLock.lock(lockKey, 20, 100, 195);
            // 检查提现订单的提交人是否当前用户
            GlWithdraw glWithdraw = glWithdrawBusiness.findById(orderId);
            if (ObjectUtils.isEmpty(glWithdraw)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_ORDER_NOT_EXIST)
                        .withDefaultValue("提现单号不存在").parse(paramBaseDO.getLanguage())).build();
            }
            if (glWithdraw.getUserId().equals(userDO.getId()) == false) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_ORDER_NOT_EXIST)
                        .withDefaultValue("提现单号不存在").parse(paramBaseDO.getLanguage())).build();
            }
            // 检查撮合订单当前状态是否允许进行确认收款
            C2CWithdrawOrderPool matchOrder = c2CWithdrawOrderPoolBusiness.findById(orderId);
            if (ObjectUtils.isEmpty(matchOrder)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_ORDER_NOT_EXIST)
                        .withDefaultValue("提现单号不存在").parse(paramBaseDO.getLanguage())).build();
            }
            if (confirmReceiveStatusArray.contains(matchOrder.getStatus()) == false) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_ORDER_STATUS_ERROR)
                        .withDefaultValue("提现单号状态错误，当前无法操作确认收款").parse(paramBaseDO.getLanguage())).build();
            }
            // 更新撮合订单状态和插入日志
            c2CWithdrawOrderPoolBusiness.confirmReceive(matchOrder, userDO);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("提现订单{}确认收款时发生异常", orderId, ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_ORDER_CONFIRM_ERROR)
                    .withDefaultValue("确认收款时发生错误").parse(paramBaseDO.getLanguage())).build();
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    public void submitReceiveTimeout(String withdrawOrderId) throws GlobalException {
        try {
            log.info("提现订单{}收款超时：准备开始执行", withdrawOrderId);
            C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.findById(withdrawOrderId);
            if (ObjectUtils.isEmpty(order)) {
                log.info("提现订单{}收款超时：未查询到充值和提现的撮合记录信息", withdrawOrderId);
                return;
            }
            if (C2CWithdrawOrderStatusEnum.WAIT_CONFIRM_RECEIVE.getStatus() != order.getStatus()) {
                log.info("提现订单{}收款超时：撮合记录当前的状态{}不是待收款，不能发送推送", withdrawOrderId, order.getStatus());
                return;
            }
            // 提现订单收款确认超时回调
            String rechargeNotifyResult = glFundC2CRechargeBusiness.rechargeNotify(order.getRechargeOrderId(), "5");
            log.info("提现订单{}收款超时：回调充值订单状态响应的结果是{}", withdrawOrderId, rechargeNotifyResult);
            // 提现订单收款确认超时回调
            String withdrawNotifyResult = glFundC2CWithdrawBusiness.withdrawNotify(order.getWithdrawOrderId(), order.getRechargeOrderId(), 5);
            log.info("提现订单{}收款超时：回调提现订单状态响应的结果是{}", withdrawOrderId, withdrawNotifyResult);
            // 发送充值订单收款超时通知
            GlRecharge glRecharge = glRechargeBusiness.findById(order.getRechargeOrderId());
            if (glRecharge != null) {
                C2CWithdrawReceiveTimeoutNoticeDO receiveTimeoutNoticeDO = new C2CWithdrawReceiveTimeoutNoticeDO();
                receiveTimeoutNoticeDO.setOrderId(order.getRechargeOrderId());
                receiveTimeoutNoticeDO.setUserId(glRecharge.getUserId());
                receiveTimeoutNoticeDO.setUsername(glRecharge.getUsername());
                noticeService.c2cRechargeReceiveTimeout(receiveTimeoutNoticeDO);
                log.info("提现订单{}收款超时：给充值订单用户发送推送成功", withdrawOrderId);
            }
            // 发送提现订单收款超时通知
            C2CWithdrawReceiveTimeoutNoticeDO withdrawReceiveTimeoutNoticeDO = new C2CWithdrawReceiveTimeoutNoticeDO();
            withdrawReceiveTimeoutNoticeDO.setOrderId(order.getWithdrawOrderId());
            withdrawReceiveTimeoutNoticeDO.setUserId(order.getUserId());
            withdrawReceiveTimeoutNoticeDO.setUsername(order.getUsername());
            noticeService.c2cWithdrawReceiveTimeout(withdrawReceiveTimeoutNoticeDO);
            log.info("提现订单{}收款超时：给提现订单用户发送推送成功", withdrawOrderId);
        } catch (Exception ex) {
            log.error("C2C提现订单{}收款超时检查发生异常", withdrawOrderId, ex);
            throw new GlobalException("C2C提现订单收款超时检查发生异常", ex);
        }
    }

    public void submitReceiveAlert(String withdrawOrderId) throws GlobalException {
        try {
            log.info("提现订单{}收款提醒：准备开始执行", withdrawOrderId);
            C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.findById(withdrawOrderId);
            if (ObjectUtils.isEmpty(order)) {
                log.info("提现订单{}收款提醒：未查询到充值和提现的撮合记录信息", withdrawOrderId);
                return;
            }
            if (C2CWithdrawOrderStatusEnum.WAIT_CONFIRM_RECEIVE.getStatus() != order.getStatus()) {
                log.info("提现订单{}收款提醒：撮合记录当前的状态{}不是待收款，不能发送推送", withdrawOrderId, order.getStatus());
                return;
            }
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            if (ObjectUtils.isEmpty(configDO)) {
                return;
            }
            C2CWithdrawReceiveAlertNoticeDO noticeDO = new C2CWithdrawReceiveAlertNoticeDO();
            noticeDO.setOrderId(withdrawOrderId);
            noticeDO.setUserId(order.getUserId());
            noticeDO.setUsername(order.getUsername());
            noticeDO.setLeftTime(configDO.getWithdrawReceiveConfirmAlertTimeout() - configDO.getWithdrawReceiveConfirmAlertTime());
            noticeService.c2cWithdrawReceiveAlert(noticeDO);
            log.info("提现订单{}收款提醒：发送提现收款提醒成功", withdrawOrderId);
        } catch (Exception ex) {
            log.error("C2C提现订单{}收款提醒检查发生异常", withdrawOrderId, ex);
            throw new GlobalException("C2C提现订单收款提醒检查发生异常", ex);
        }
    }

    public void submitPaymentTimeout(String rechargeOrderId) throws GlobalException {
        try {
            log.info("充值订单{}付款超时：准备开始执行", rechargeOrderId);
            GlRecharge glRecharge = glRechargeBusiness.findById(rechargeOrderId);
            if (ObjectUtils.isEmpty(glRecharge)) {
                log.info("充值订单{}付款超时：未查询到充值记录信息", rechargeOrderId);
                return;
            }
            C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.getByRechargeOrderId(rechargeOrderId);
            if (ObjectUtils.isEmpty(order)) {
                log.info("充值订单{}付款超时：未查询到充值和提现的撮合记录信息", rechargeOrderId);
                return;
            }
            if (C2CWithdrawOrderStatusEnum.WAIT_PAYMENT.getStatus() != order.getStatus()) {
                log.info("充值订单{}付款超时：撮合记录当前的状态{}不是待付款，不能发送推送", rechargeOrderId, order.getStatus());
                return;
            }
            // 更新提现订单当前状态为待付款
            c2CWithdrawOrderPoolBusiness.updateCancel(order.getWithdrawOrderId());
            // 保存订单号的撮合的日志
            c2CMatchLogBusiness.rechargeOrderCancelLog(order, glRecharge.getUserId(), glRecharge.getUsername());
            // 回调充值订单进入待确认到账状态
            String rechargeNotifyResult = glFundC2CRechargeBusiness.rechargeNotify(order.getRechargeOrderId(), "4");
            log.info("充值订单{}付款超时：回调充值订单状态响应的结果是{}", rechargeOrderId, rechargeNotifyResult);
            // 回调提现订单进入待确认到账状态
            String withdrawNotifyResult = glFundC2CWithdrawBusiness.withdrawNotify(order.getWithdrawOrderId(), order.getRechargeOrderId(), 4);
            log.info("充值订单{}付款超时：回调提现订单状态响应的结果是{}", rechargeOrderId, withdrawNotifyResult);
            // 发送通知和推送
            C2CRechargePaymentTimeoutNoticeDO noticeDO = new C2CRechargePaymentTimeoutNoticeDO();
            noticeDO.setOrderId(rechargeOrderId);
            noticeDO.setUserId(glRecharge.getUserId());
            noticeDO.setUsername(glRecharge.getUsername());
            noticeService.c2cRechargePaymentTimeout(noticeDO);
            log.info("充值订单{}付款超时：发送充值付款超时取消订单的推送成功", rechargeOrderId);
        } catch (Exception ex) {
            log.error("C2C充值订单{}付款超时检查发生异常", rechargeOrderId, ex);
            throw new GlobalException("C2C充值订单付款超时检查发生异常", ex);
        }
    }

    public void submitPaymentAlert(String rechargeOrderId) throws GlobalException {
        try {
            log.info("充值订单{}付款提醒：准备开始执行", rechargeOrderId);
            GlRecharge glRecharge = glRechargeBusiness.findById(rechargeOrderId);
            if (ObjectUtils.isEmpty(glRecharge)) {
                log.info("充值订单{}付款提醒：未查询到充值记录信息", rechargeOrderId);
                return;
            }
            C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.getByRechargeOrderId(rechargeOrderId);
            if (ObjectUtils.isEmpty(order)) {
                log.info("充值订单{}付款提醒：未查询到充值和提现的撮合记录信息", rechargeOrderId);
                return;
            }
            if (C2CWithdrawOrderStatusEnum.WAIT_PAYMENT.getStatus() != order.getStatus()) {
                log.info("充值订单{}付款提醒：撮合记录当前的状态{}不是待付款，不能发送推送", rechargeOrderId, order.getStatus());
                return;
            }
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            if (ObjectUtils.isEmpty(configDO)) {
                return;
            }
            C2CRechargePaymentAlertNoticeDO noticeDO = new C2CRechargePaymentAlertNoticeDO();
            noticeDO.setOrderId(rechargeOrderId);
            noticeDO.setUserId(glRecharge.getUserId());
            noticeDO.setUsername(glRecharge.getUsername());
            noticeDO.setLeftTime(configDO.getRechargePaymentTimeout() - configDO.getRechargeAlertTime());
            noticeService.c2cRechargePaymentAlert(noticeDO);
            log.info("充值订单{}付款提醒：发送充值付款提醒成功", rechargeOrderId);
        } catch (Exception ex) {
            log.error("C2C充值订单{}付款提醒检查发生异常", rechargeOrderId, ex);
            throw new GlobalException("C2C充值订单付款提醒检查发生异常", ex);
        }
    }

    public void submitUnlock(String withdrawOrderId) throws GlobalException {
        String lockKey = "C2C_RECHARGE_MATCH_REDIS_LOCK_KEY";
        try {
            if (StringUtils.isEmpty(withdrawOrderId)) {
                log.error("撮合解锁：提现订单{}为空，不进行解锁处理", withdrawOrderId);
                return;
            }
            redisLock.lock(lockKey, 20, 100, 195);
            C2CWithdrawOrderPool orderPool = c2CWithdrawOrderPoolBusiness.findById(withdrawOrderId);
            if (ObjectUtils.isEmpty(orderPool)) {
                log.error("撮合解锁：提现订单{}在撮合池未查询到数据，不进行解锁处理", withdrawOrderId);
                return;
            }
            if (C2CWithdrawOrderStatusEnum.PENDING.getStatus() != orderPool.getStatus()) {
                log.error("撮合解锁：提现订单{}当前的状态是{}，不是待撮合状态，不进行解锁处理", withdrawOrderId, orderPool.getStatus());
                return;
            }
            log.info("撮合解锁：提现订单{}在撮合池准备解除锁定", withdrawOrderId);
            c2COrderTransactionalHandler.matchUnLockAndLog(withdrawOrderId);
            log.info("撮合解锁：提现订单{}在撮合池成功解除锁定", withdrawOrderId);
        } catch (Exception ex) {
            log.error("解锁撮合订单{}的锁定状态发生异常", withdrawOrderId, ex);
            throw new GlobalException("解锁撮合订单的锁定状态发生异常", ex);
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    private void sendPaymentAlertToWithdrawUser(C2CWithdrawOrderPool order) {
        C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
        if (ObjectUtils.isEmpty(configDO)) {
            return;
        }
        C2CWithdrawPaymentAlertNoticeDO noticeDO = new C2CWithdrawPaymentAlertNoticeDO();
        noticeDO.setOrderId(order.getWithdrawOrderId());
        noticeDO.setUserId(order.getUserId());
        noticeDO.setUsername(order.getUsername());
        noticeDO.setLeftTime(configDO.getWithdrawReceiveConfirmAlertTimeout());
        noticeService.c2cWithdrawPaymentAlert(noticeDO);
    }

    protected void receiveAlertAndTimeoutReport(String orderId) {
        if (StringUtils.isEmpty(orderId)) {
            return;
        }
        C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
        if (ObjectUtils.isEmpty(configDO)) {
            return;
        }
        // 提现订单收款提醒延时上报
        reportService.c2cWithdrawReceiveAlertReport(new C2CWithdrawReceiveAlertReport(orderId, Long.valueOf(configDO.getWithdrawReceiveConfirmAlertTime() * 60 * 1000)));
        // 提现订单收款超时延时上报
        reportService.c2cWithdrawReceiveTimeoutReport(new C2CWithdrawReceiveTimeoutReport(orderId, Long.valueOf(configDO.getWithdrawReceiveConfirmAlertTimeout() * 60 * 1000)));
    }

    /**
     * 通过提现单号获取撮合详情
     *
     * @param withdrawOrderId
     * @return
     */
    public C2COrderDetailResult getByWithdrawOrderId(String withdrawOrderId) {
        String lockKey = "C2C_WITHDRAW_CONFIRM_RECEIVE_REDIS_LOCK_KEY_" + withdrawOrderId;
        try {
            redisLock.lock(lockKey, 20, 100, 195);
            C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.findById(withdrawOrderId);
            C2COrderDetailResult detailResult = new C2COrderDetailResult();
            detailResult.setWithdrawOrderId(withdrawOrderId);
            if (ObjectUtils.isEmpty(order)) {
                detailResult.setCreateDate(c2CMatchLogBusiness.getCreateDate(withdrawOrderId, C2CMatchLogTypeEnum.WITHDRAW_CREATE));
                detailResult.setMatchedDate(c2CMatchLogBusiness.getCreateDate(withdrawOrderId, C2CMatchLogTypeEnum.MATCHED));
                detailResult.setPaymentDate(c2CMatchLogBusiness.getCreateDate(withdrawOrderId, C2CMatchLogTypeEnum.CONFIRM_PAYMENT));
                detailResult.setReceiveDate(c2CMatchLogBusiness.getCreateDate(withdrawOrderId, C2CMatchLogTypeEnum.CONFIRM_RECEIVE));
            } else {
                detailResult.setRechargeOrderId(order.getRechargeOrderId());
                detailResult.setMatchAmount(order.getWithdrawAmount());
                detailResult.setStatus(order.getStatus());
                detailResult.setCreateDate(order.getCreateDate());
                detailResult.setMatchedDate(order.getMatchedDate());
                detailResult.setPaymentDate(order.getPaymentDate());
                detailResult.setReceiveDate(order.getReceiveDate());
            }
            return detailResult;
        } catch (Exception ex) {
            log.error("查询提现订单的{}撮合详情时发生异常", withdrawOrderId, ex);
            return null;
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    /**
     * 通过提现单号和充值订单号获取撮合详情
     *
     * @param withdrawOrderId
     * @param rechargeOrderId
     * @return
     */
    public C2COrderDetailResult getByWithdrawOrderId(String withdrawOrderId, String rechargeOrderId) {
        C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.findById(withdrawOrderId);
        C2COrderDetailResult detailResult = new C2COrderDetailResult();
        detailResult.setWithdrawOrderId(withdrawOrderId);
        if (ObjectUtils.isEmpty(order)) {
            detailResult.setCreateDate(c2CMatchLogBusiness.getCreateDate(withdrawOrderId, rechargeOrderId, C2CMatchLogTypeEnum.WITHDRAW_CREATE));
            detailResult.setMatchedDate(c2CMatchLogBusiness.getCreateDate(withdrawOrderId, rechargeOrderId, C2CMatchLogTypeEnum.MATCHED));
            detailResult.setPaymentDate(c2CMatchLogBusiness.getCreateDate(withdrawOrderId, rechargeOrderId, C2CMatchLogTypeEnum.CONFIRM_PAYMENT));
            detailResult.setReceiveDate(c2CMatchLogBusiness.getCreateDate(withdrawOrderId, rechargeOrderId, C2CMatchLogTypeEnum.CONFIRM_RECEIVE));
        } else {
            detailResult.setRechargeOrderId(order.getRechargeOrderId());
            detailResult.setMatchAmount(order.getWithdrawAmount());
            detailResult.setStatus(order.getStatus());
            detailResult.setCreateDate(order.getCreateDate());
            detailResult.setMatchedDate(order.getMatchedDate());
            detailResult.setPaymentDate(order.getPaymentDate());
            detailResult.setReceiveDate(order.getReceiveDate());
        }
        return detailResult;
    }

    /**
     * 通过充值单号获取撮合详情
     *
     * @param rechargeOrderId
     * @return
     */
    public C2COrderDetailResult getByRechargeOrderId(String rechargeOrderId) {
        C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.getByRechargeOrderId(rechargeOrderId);
        C2COrderDetailResult detailResult = new C2COrderDetailResult();
        detailResult.setRechargeOrderId(rechargeOrderId);
        if (ObjectUtils.isEmpty(order)) {
            detailResult.setMatchedDate(c2CMatchLogBusiness.getCreateDate(rechargeOrderId, C2CMatchLogTypeEnum.MATCHED));
            detailResult.setPaymentDate(c2CMatchLogBusiness.getCreateDate(rechargeOrderId, C2CMatchLogTypeEnum.CONFIRM_PAYMENT));
            detailResult.setReceiveDate(c2CMatchLogBusiness.getCreateDate(rechargeOrderId, C2CMatchLogTypeEnum.CONFIRM_RECEIVE));
        } else {
            detailResult.setWithdrawOrderId(order.getWithdrawOrderId());
            detailResult.setMatchAmount(order.getWithdrawAmount());
            detailResult.setStatus(order.getStatus());
            detailResult.setCreateDate(order.getCreateDate());
            detailResult.setMatchedDate(order.getMatchedDate());
            detailResult.setPaymentDate(order.getPaymentDate());
            detailResult.setReceiveDate(order.getReceiveDate());
        }
        return detailResult;
    }

    /**
     * 通过充值单号和提现单号获取撮合详情
     *
     * @param rechargeOrderId
     * @param withdrawOrderId
     * @return
     */
    public C2COrderDetailResult getByRechargeOrderId(String rechargeOrderId, String withdrawOrderId) {
        C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.getByRechargeOrderId(rechargeOrderId);
        C2COrderDetailResult detailResult = new C2COrderDetailResult();
        detailResult.setRechargeOrderId(rechargeOrderId);
        if (ObjectUtils.isEmpty(order)) {
            detailResult.setMatchedDate(c2CMatchLogBusiness.getCreateDate(rechargeOrderId, withdrawOrderId, C2CMatchLogTypeEnum.MATCHED));
            detailResult.setPaymentDate(c2CMatchLogBusiness.getCreateDate(rechargeOrderId, withdrawOrderId, C2CMatchLogTypeEnum.CONFIRM_PAYMENT));
            detailResult.setReceiveDate(c2CMatchLogBusiness.getCreateDate(rechargeOrderId, withdrawOrderId, C2CMatchLogTypeEnum.CONFIRM_RECEIVE));
        } else {
            detailResult.setWithdrawOrderId(order.getWithdrawOrderId());
            detailResult.setMatchAmount(order.getWithdrawAmount());
            detailResult.setStatus(order.getStatus());
            detailResult.setCreateDate(order.getCreateDate());
            detailResult.setMatchedDate(order.getMatchedDate());
            detailResult.setPaymentDate(order.getPaymentDate());
            detailResult.setReceiveDate(order.getReceiveDate());
        }
        return detailResult;
    }

    /**
     * 提现订单未撮合   true: 未撮合    false ： 已撮合
     * @param withdrawOrderId
     * @return
     */
    public boolean checkWithdrawIsMatching(String withdrawOrderId) throws GlobalException {
        String lockKey = "C2C_RECHARGE_MATCH_REDIS_LOCK_KEY";
        try {
            redisLock.lock(lockKey, 20, 100, 195);
            C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.findById(withdrawOrderId);
            if (ObjectUtils.isEmpty(order)) {
                return true;
            }
            if (C2CWithdrawOrderStatusEnum.PENDING.getStatus() != order.getStatus()) {
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.error("提现订单撮合状态检查发生异常，提现单号：{}", withdrawOrderId, ex);
            throw new GlobalException("提现订单撮合状态检查发生异常", ex);
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

}