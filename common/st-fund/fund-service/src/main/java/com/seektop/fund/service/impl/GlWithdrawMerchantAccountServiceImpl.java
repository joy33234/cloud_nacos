package com.seektop.fund.service.impl;

import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlFundMerchantWithdrawBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawMerchantAccountBusiness;
import com.seektop.fund.dto.param.withdraw.GlWithdrawMerchantAccountDO;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.GlPaymentWithdrawHandler;
import com.seektop.fund.payment.GlWithdrawHandlerManager;
import com.seektop.fund.service.GlWithdrawMerchantAccountService;
import com.seektop.fund.vo.WithdrawMerchantBalanceResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
@Service(retries = 2, timeout = 3000, interfaceClass = GlWithdrawMerchantAccountService.class)
public class GlWithdrawMerchantAccountServiceImpl implements GlWithdrawMerchantAccountService {

    @Resource
    private GlWithdrawMerchantAccountBusiness glWithdrawMerchantAccountBusiness;

    @Resource
    private GlFundMerchantWithdrawBusiness glFundMerchantWithdrawBusiness;

    @Resource
    private GlWithdrawHandlerManager glWithdrawHandlerManager;

    @Resource
    private RedisService redisService;

    @Override
    public RPCResponse<List<GlWithdrawMerchantAccountDO>> findValidMerchantAccount() {
        return RPCResponseUtils.buildSuccessRpcResponse(DtoUtils.transformList(glWithdrawMerchantAccountBusiness.findValidMerchantAccount(), GlWithdrawMerchantAccountDO.class));
    }

    @Override
    public void doWithdrawMerchantQueryBalance(GlWithdrawMerchantAccountDO accountDO) throws GlobalException {
        GlWithdrawMerchantAccount account = glWithdrawMerchantAccountBusiness.findById(accountDO.getMerchantId());
        if (null == account) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }

        String key = String.format(RedisKeyHelper.WITHDRAW_MERCHANT_BANLANCE, account.getMerchantId());
        BigDecimal balance = null;
        try {

            balance = redisService.get(key, BigDecimal.class);
            if (null == balance) {
                GlPaymentWithdrawHandler handler = glWithdrawHandlerManager.getPaymentWithdrawHandler(account);
                if (null == handler) {
                    log.error("no_handler_for_withdraw_channel_{}.", account.getChannelName());
                    throw new GlobalException(ResultCode.DATA_ERROR);
                }

                balance = handler.queryBalance(account);
            }
        } catch (Exception e) {
            log.error("doWithdrawMerchantQueryBalance_err:{}", e);
        }
        if (null == balance) {
            balance = BigDecimal.ZERO;
        }
        redisService.set(key, balance, 30);

        WithdrawMerchantBalanceResult balanceResult = new WithdrawMerchantBalanceResult();
        balanceResult.setMerchantId(account.getMerchantId());
        balanceResult.setStatus(account.getStatus());
        balanceResult.setBalance(balance);
        balanceResult.setUpdateTime(new Date());
        glFundMerchantWithdrawBusiness.syncMerchantBalance(balanceResult);

    }

}
