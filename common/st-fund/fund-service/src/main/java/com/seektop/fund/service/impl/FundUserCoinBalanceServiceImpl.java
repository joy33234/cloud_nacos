package com.seektop.fund.service.impl;

import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.constant.HandlerResponseCode;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.fund.FundReportEvent;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserCoinBalanceBusiness;
import com.seektop.fund.dto.param.account.UserCoinAccountChangeDO;
import com.seektop.fund.dto.result.account.FundUserCoinAccountDO;
import com.seektop.fund.model.GlFundUserCoinAccount;
import com.seektop.fund.service.FundUserCoinBalanceService;
import com.seektop.report.fund.HandlerResponse;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import java.util.Objects;

/**
 * @Auther: walter
 * @Date: 8/2/20 14:37
 * @Description:
 */
@Slf4j
@Service(timeout = 5000, interfaceClass = FundUserCoinBalanceService.class)
public class FundUserCoinBalanceServiceImpl implements FundUserCoinBalanceService {

    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;

    @Resource
    private GlFundUserCoinBalanceBusiness glFundUserCoinBalanceBusiness;

    @Resource
    private RedisService redisService;

    /**
     * 根据userId获取玩家金币余额
     * @param userId 玩家id
     * @return
     */
    @Override
    public RPCResponse<FundUserCoinAccountDO> getFundUserCoinBalance(@Nonnull Integer userId) {
        RPCResponse<GlUserDO> resp = glUserService.findById(userId);
        GlUserDO glUserDO = resp.getData();
        // userId
        if (Objects.isNull(glUserDO)) {
            log.error("userId[{}]对应的用户不存在", userId);
            return RPCResponse.<FundUserCoinAccountDO>newBuilder().fail().setMessage("userId对应的用户不存在").build();
        }
        try {
            GlFundUserCoinAccount glFundUserCoinAccount = glFundUserCoinBalanceBusiness.getFundUserCoinBalance(glUserDO);
            FundUserCoinAccountDO fundUserCoinAccountDO = new FundUserCoinAccountDO();
            BeanUtils.copyProperties(glFundUserCoinAccount, fundUserCoinAccountDO);
            return RPCResponse.<FundUserCoinAccountDO>newBuilder().success().setData(fundUserCoinAccountDO).build();
        } catch (GlobalException e) {
            log.error("获取玩家金币额度失败 {}", e.getExtraMessage(), e);
            return RPCResponse.<FundUserCoinAccountDO>newBuilder().fail().setMessage("获取用户金币额度失败").build();
        }
    }

    @Override
    public HandlerResponse fundUserCoinAccountChange(UserCoinAccountChangeDO userCoinAccountChangeDO) {
        String tradeId = userCoinAccountChangeDO.getTradeId();
        String key = RedisKeyHelper.GL_FUND_COIN_CHANGE_LOCK + tradeId;
        try {
            // 防止重复提交
            if (redisService.incrBy(key, 1) > 1) {
                return HandlerResponse.generateRespoese(tradeId, FundReportEvent.COIN_CHANGE.value(), HandlerResponseCode.FAIL.getCode(), "提交过于频繁，请稍等片刻");
            }
            redisService.setTTL(key, 2);
            Integer userId = userCoinAccountChangeDO.getUserId();
            RPCResponse<GlUserDO> resp = glUserService.findById(userId);
            GlUserDO glUserDO = resp.getData();
            // userId
            if (Objects.isNull(glUserDO)) {
                log.error("userId[{}]对应的用户不存在", userId);
                return HandlerResponse.generateRespoese(tradeId, FundReportEvent.COIN_CHANGE.value(), HandlerResponseCode.FAIL.getCode(), "userId对应的用户不存在");
            }
            GlFundUserCoinAccount glFundUserCoinAccount = glFundUserCoinBalanceBusiness.getFundUserCoinBalance(glUserDO);
            glFundUserCoinBalanceBusiness.doCoinBalanceChange(userCoinAccountChangeDO, glUserDO, glFundUserCoinAccount);
            return HandlerResponse.generateRespoese(tradeId, FundReportEvent.COIN_CHANGE.value(), HandlerResponseCode.SUCCESS.getCode(), "加减金币成功");
        } catch (GlobalException e) {
            log.error("加减金币异常 请求参数{} 异常信息{}", userCoinAccountChangeDO, e.getExtraMessage(), e);
            return HandlerResponse.generateRespoese(tradeId, FundReportEvent.COIN_CHANGE.value(), HandlerResponseCode.FAIL.getCode(), "加减币异常：" + e.getExtraMessage());
        } finally {
            // 释放锁
            redisService.delete(key);
        }

    }
}
