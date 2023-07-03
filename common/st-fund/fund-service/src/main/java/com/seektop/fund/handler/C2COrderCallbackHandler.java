package com.seektop.fund.handler;

import com.alibaba.fastjson.JSON;
import com.seektop.common.redis.RedisLock;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.fund.C2CWithdrawOrderStatusEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundC2CRechargeBusiness;
import com.seektop.fund.business.GlFundC2CWithdrawBusiness;
import com.seektop.fund.business.c2c.C2CMatchLogBusiness;
import com.seektop.fund.business.c2c.C2CWithdrawOrderPoolBusiness;
import com.seektop.fund.common.C2COrderUtils;
import com.seektop.fund.common.C2CRechargeOrderCreateResult;
import com.seektop.fund.common.C2CRechargeOrderMatchResult;
import com.seektop.fund.model.C2CWithdrawOrderPool;
import com.seektop.system.dto.param.C2CWithdrawCreateNoticeDO;
import com.seektop.system.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class C2COrderCallbackHandler {

    private final RedisLock redisLock;
    private final C2COrderUtils c2COrderUtils;

    private final GlFundC2CRechargeBusiness glFundC2CRechargeBusiness;
    private final GlFundC2CWithdrawBusiness glFundC2CWithdrawBusiness;
    private final C2CMatchLogBusiness c2CMatchLogBusiness;
    private final C2CWithdrawOrderPoolBusiness c2CWithdrawOrderPoolBusiness;

    private final C2COrderTransactionalHandler c2COrderTransactionalHandler;

    @DubboReference(timeout = 5000, retries = 1)
    private NoticeService noticeService;

    /**
     * 充值匹配检查
     *
     * @param userDO
     * @param amount
     * @param ip
     * @return
     * @throws GlobalException
     */
    public C2CRechargeOrderMatchResult rechargeMatch(GlUserDO userDO, BigDecimal amount, String ip) throws GlobalException {
        String lockKey = "C2C_RECHARGE_MATCH_REDIS_LOCK_KEY";
        try {
            redisLock.lock(lockKey, 20, 100, 195);
            C2CRechargeOrderMatchResult result = new C2CRechargeOrderMatchResult();
            // 查询满足当前条件待撮合的提现订单
            List<C2CWithdrawOrderPool> matchedOrderList = c2CWithdrawOrderPoolBusiness.matchWithdrawOrder(amount, userDO.getId(), ip);
            // 如果找不到>=充值金额的提现订单，向下查找<充值金额的提现订单
            if (CollectionUtils.isEmpty(matchedOrderList)) {
                matchedOrderList = c2CWithdrawOrderPoolBusiness.matchWithdrawOrderByLessThan(amount, userDO.getId(), ip);
            }
            // 没有撮合到符合条件的订单
            if (CollectionUtils.isEmpty(matchedOrderList)) {
                result.setMatchedResult(0);
                log.info("极速转卡-充值匹配结果：用户{}请求充值金额{}，匹配不到提现订单", userDO.getUsername(), amount);
                return result;
            }
            C2CWithdrawOrderPool matchedOrder = matchedOrderList.get(0);
            if (matchedOrder.getWithdrawAmount().compareTo(amount) == 0) {
                // 撮合成功(指定金额)
                log.info("极速转卡-充值匹配结果：用户{}请求充值金额{}，按照请求金额匹配成功，匹配的提现单号是{}", userDO.getUsername(), amount, matchedOrder.getWithdrawOrderId());
                result.setMatchedResult(1);
            } else {
                // 撮合成功(推荐金额)
                log.info("极速转卡-充值匹配结果：用户{}请求充值金额{}，按照推荐金额匹配成功，匹配的提现单号是{}", userDO.getUsername(), amount, matchedOrder.getWithdrawOrderId());
                result.setMatchedResult(2);
                result.setRecommendAmount(matchedOrder.getWithdrawAmount());
            }
            // 更新锁定状态和保存撮合日志
            c2COrderTransactionalHandler.matchLockAndLog(matchedOrder.getWithdrawOrderId(), userDO, amount);
            // 异步上报撮合订单锁定
            c2COrderUtils.c2cOrderUnlockReport(matchedOrder.getWithdrawOrderId());
            log.info("极速转卡-充值匹配结果：提现订单{}被充值订单匹配，锁定10秒", matchedOrder.getWithdrawOrderId());
            return result;
        } catch (Exception ex) {
            log.error("极速转卡-充值匹配结果：用户{}请求充值金额{}，发生异常", userDO.getUsername(), amount, ex);
            throw new GlobalException("极速转卡-充值订单匹配检查时发生异常", ex);
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    /**
     * 充值创建订单
     *
     * @param userDO
     * @param amount
     * @param orderId
     * @param ip
     * @return
     * @throws GlobalException
     */
    public C2CRechargeOrderCreateResult rechargeCreate(GlUserDO userDO, BigDecimal amount, String orderId, String ip) throws GlobalException {
        String lockKey = "C2C_RECHARGE_MATCH_REDIS_LOCK_KEY";
        try {
            redisLock.lock(lockKey, 20, 100, 195);
            C2CRechargeOrderCreateResult result = new C2CRechargeOrderCreateResult();
            result.setRechargeOrderId(orderId);
            // 检查充值订单是否存在已经撮合的数据
            C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.getByRechargeOrderId(orderId);
            if (ObjectUtils.isEmpty(order) == false) {
                log.error("极速转卡-充值创建订单，用户{}请求充值{}，订单号{}已经存在撮合数据，不能重复提交", userDO.getUsername(), amount, orderId);
                result.setCode(2);
                result.setMessage("充值订单号" + orderId + "已经存在撮合数据，不能重复提交");
                return result;
            }
            // 检查金额是否可以匹配
            List<C2CWithdrawOrderPool> matchedOrderList = c2CWithdrawOrderPoolBusiness.matchWithdrawOrder(amount, userDO.getId(), ip);
            // 如果找不到>=充值金额的提现订单，向下查找<充值金额的提现订单
            if (CollectionUtils.isEmpty(matchedOrderList)) {
                matchedOrderList = c2CWithdrawOrderPoolBusiness.matchWithdrawOrderByLessThan(amount, userDO.getId(), ip);
            }
            if (CollectionUtils.isEmpty(matchedOrderList)) {
                log.error("极速转卡-充值创建订单，用户{}请求充值{}，订单号{}，匹配不到提现订单，无法创建", userDO.getUsername(), amount, orderId);
                result.setCode(3);
                result.setMessage("充值订单号：" + orderId + "，匹配不到提现订单");
                return result;
            }
            order = matchedOrderList.get(0);
            if (order.getWithdrawAmount().compareTo(amount) != 0) {
                log.error("极速转卡-充值创建订单，用户{}请求充值{}，订单号{}，匹配到的提现订单金额不符，无法创建", userDO.getUsername(), amount, orderId);
                result.setCode(4);
                result.setMessage("充值订单号：" + orderId + "，匹配到的提现订单金额不符");
                return result;
            }
            log.info("极速转卡-充值创建订单：提现订单{}成功匹配充值订单{}，准备更新锁定和待付款状态", order.getWithdrawOrderId(), orderId);
            // 更新撮合状态为待付款和保存撮合日志
            c2COrderTransactionalHandler.rechargeCreateAndLog(order.getWithdrawOrderId(), orderId, userDO, amount);
            // 回调提现订单状态更新为待付款
            glFundC2CWithdrawBusiness.withdrawNotify(order.getWithdrawOrderId(), orderId, 1);
            // 回调充值订单状态更新为待付款
            glFundC2CRechargeBusiness.rechargeNotify(orderId, "1");
            // 异步上报充值订单付款提醒和超时延时
            c2COrderUtils.paymentAlertAndTimeoutReport(orderId);
            log.info("极速转卡-充值创建订单：提现订单{}成功匹配充值订单{}，成功更新锁定和待付款状态", order.getWithdrawOrderId(), orderId);
            // 组装返回结果
            result.setWithdrawOrderId(order.getWithdrawOrderId());
            result.setStatus(order.getStatus());
            result.setCode(1);
            return result;
        } catch (Exception ex) {
            log.error("极速转卡-充值创建订单：用户{}请求充值金额{}，充值订单号{}，发生异常", userDO.getUsername(), amount, orderId, ex);
            throw new GlobalException("极速转卡-充值创建订单时发生异常", ex);
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    /**
     * 充值订单撤销
     *
     * @param userDO
     * @param orderId
     * @throws GlobalException
     */
    public void rechargeCancel(GlUserDO userDO, String orderId) throws GlobalException {
        String lockKey = "C2C_RECHARGE_MATCH_REDIS_LOCK_KEY";
        try {
            redisLock.lock(lockKey, 20, 100, 195);
            // 检查充值订单是否存在已经撮合的数据
            C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.getByRechargeOrderId(orderId);
            if (ObjectUtils.isEmpty(order)) {
                return;
            }
            log.info("极速转卡-充值订单撤销：提现订单{}对应的充值订单{}准备撤销", order.getWithdrawOrderId(), orderId);
            // 撮合取消锁定/更新为待撮合状态/保存撮合日志
            c2COrderTransactionalHandler.rechargeCancelAndLog(order, userDO);
            // 回调提现订单
            glFundC2CWithdrawBusiness.withdrawNotify(order.getWithdrawOrderId(), order.getRechargeOrderId(), 6);
            log.info("极速转卡-充值订单撤销：提现订单{}对应的充值订单{}成功撤销", order.getWithdrawOrderId(), orderId);
        } catch (Exception ex) {
            log.error("极速转卡-充值订单撤销：用户{}撤销充值订单号{}，发生异常", userDO.getUsername(), orderId, ex);
            throw new GlobalException("极速转卡-充值订单撤销时发生异常", ex);
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    /**
     * 提现订单撤销
     *
     * @param orderId   提现订单号
     * @param enforce   是否强制撤销
     * @return
     * @throws GlobalException
     */
    public Boolean withdrawCancel(String orderId, boolean enforce) throws GlobalException {
        String lockKey = "C2C_RECHARGE_MATCH_REDIS_LOCK_KEY";
        try {
            redisLock.lock(lockKey, 20, 100, 195);
            log.info("极速转卡-提现订单撤销：提现订单{}准备撤销", orderId);
            // 检查提现订单是否存在于撮合池
            C2CWithdrawOrderPool order = c2CWithdrawOrderPoolBusiness.findById(orderId);
            if (ObjectUtils.isEmpty(order)) {
                return false;
            }
            // 非强制删除时，只有待撮合状态能删除
            log.info("定位撮合成功删除提现订单_删除时提现订单撮合状态:{}，enforce:{}", JSON.toJSONString(order), enforce);
            if (enforce == false && order.getStatus() != C2CWithdrawOrderStatusEnum.PENDING.getStatus()) {
                return false;
            }
            // 删除撮合池中的提现订单和保存撮合日志
            c2COrderTransactionalHandler.withdrawCancelAndLog(order);
            log.info("极速转卡-提现订单撤销：提现订单{}成功撤销", orderId);
            return true;
        } catch (Exception ex) {
            log.error("极速转卡-提现订单撤销：提现订单{}撤销时，发生异常", orderId, ex);
            throw new GlobalException("极速转卡-提现订单撤销时发生异常", ex);
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    /**
     * 提现订单创建
     *
     * @param userDO
     * @param amount
     * @param orderId
     * @throws GlobalException
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void withdrawCreate(GlUserDO userDO, BigDecimal amount, String orderId, String ip) throws GlobalException {
        try {
            C2CWithdrawOrderPool withdrawOrderPool = c2CWithdrawOrderPoolBusiness.findById(orderId);
            if (ObjectUtils.isEmpty(withdrawOrderPool) == false) {
                log.error("极速转卡-提现创建：用户{}请求提现金额{}，提现单号{}，提现单号已经存在，创建失败", userDO.getUsername(), amount, orderId);
                throw new GlobalException("极速转卡-提现订单" + orderId + "创建时发生异常：提现单号已经存在");
            }
            withdrawOrderPool = new C2CWithdrawOrderPool();
            withdrawOrderPool.setWithdrawOrderId(orderId);
            withdrawOrderPool.setUserId(userDO.getId());
            withdrawOrderPool.setUsername(userDO.getUsername());
            withdrawOrderPool.setIp(ip);
            withdrawOrderPool.setWithdrawAmount(amount);
            withdrawOrderPool.setStatus(C2CWithdrawOrderStatusEnum.PENDING.getStatus());
            withdrawOrderPool.setIsLocked(false);
            withdrawOrderPool.setCreateDate(new Date());
            c2CWithdrawOrderPoolBusiness.save(withdrawOrderPool);
            // 保存撮合日志
            c2CMatchLogBusiness.withdrawOrderCreateLog(orderId, userDO, amount);
            // 提现订单被撮合确认通知
            sendWithdrawCreateNotice(withdrawOrderPool);
            log.error("极速转卡-提现创建：用户{}请求提现金额{}，提现单号{}，创建成功", userDO.getUsername(), amount, orderId);
        } catch (Exception ex) {
            log.error("极速转卡-提现创建：用户{}请求提现金额{}，提现单号{}，发生异常", userDO.getUsername(), amount, orderId, ex);
            throw new GlobalException("极速转卡-提现订单创建时发生异常", ex);
        }
    }

    protected void sendWithdrawCreateNotice(C2CWithdrawOrderPool orderPool) {
        C2CWithdrawCreateNoticeDO noticeDO = new C2CWithdrawCreateNoticeDO();
        noticeDO.setOrderId(orderPool.getWithdrawOrderId());
        noticeDO.setUserId(orderPool.getUserId());
        noticeDO.setUsername(orderPool.getUsername());
        noticeService.c2cWithdrawCreate(noticeDO);
    }

}