package com.seektop.fund.handler.impl;

import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.utils.DateUtils;
import com.seektop.fund.business.GlWithdrawEffectBetBusiness;
import com.seektop.fund.dto.result.withdraw.GlWithdrawEffectBetDO;
import com.seektop.fund.handler.UserSyncHandler;
import com.seektop.report.user.BalanceDetailDO;
import com.seektop.report.user.UserSynch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

@Slf4j
@Component
public class UserSyncHandlerImpl implements UserSyncHandler {

    @Resource
    private ReportService reportService;
    @Resource
    private GlWithdrawEffectBetBusiness glWithdrawEffectBetBusiness;

    public void userBalanceSync(Integer userId, String coin, BigDecimal balance){
        try {
            UserSynch userSynch = new UserSynch();
            userSynch.setId(userId);
            userSynch.setBalanceDetail(Arrays.asList(new BalanceDetailDO(coin, balance)));
            userSynch.setBalance(balance);
            GlWithdrawEffectBetDO betDO = glWithdrawEffectBetBusiness.getWithdrawEffectBetDO(userId,coin);
            userSynch.setFreeze_balance(betDO.getEffectAmount());
            userSynch.setFreezeBalanceDetail(Arrays.asList(new BalanceDetailDO(coin, betDO.getEffectAmount())));
            userSynch.setValid_balance(betDO.getValidBalance());
            userSynch.setValidBalanceDetail(Arrays.asList(new BalanceDetailDO(coin, betDO.getValidBalance())));
            userSynch.setLast_update(DateUtils.format(new Date(), "yyyy-MM-dd'T'HH:mm:ss'.000Z'"));
            log.info("sync user betDO = {}, balance = {}", betDO, balance);
            reportService.userSynch(userSynch);
        } catch (Exception e) {
            log.error("reportService.userSynch error, userId = {}， balance = {}", userId, balance);
            log.error(e.getMessage(), e);
        }
    }

}