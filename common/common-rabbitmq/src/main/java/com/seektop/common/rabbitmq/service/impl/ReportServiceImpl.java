package com.seektop.common.rabbitmq.service.impl;

import com.alibaba.fastjson.JSON;
import com.seektop.common.rabbitmq.producer.DefaultProducer;
import com.seektop.common.rabbitmq.producer.ReportProducer;
import com.seektop.common.rabbitmq.producer.UserProducer;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.enumerate.ReportEvent;
import com.seektop.enumerate.user.UserReportEvent;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.report.activity.ActivityBaseReport;
import com.seektop.report.activity.GoldMallBalanceRecordReport;
import com.seektop.report.activity.RebateReport;
import com.seektop.report.agent.*;
import com.seektop.report.common.BalanceRecordReport;
import com.seektop.report.common.BonusReport;
import com.seektop.report.digital.*;
import com.seektop.report.fund.*;
import com.seektop.report.game.GameUserBalanceReport;
import com.seektop.report.gamebet.BettingReport;
import com.seektop.report.gamebet.GameTransferReport;
import com.seektop.report.live.LiveFundRecordReport;
import com.seektop.report.live.LiveGiftPresentReport;
import com.seektop.report.live.LivePlazaInformationGrabReport;
import com.seektop.report.notice.NoticeReport;
import com.seektop.report.sharewallet.ShareWalletReport;
import com.seektop.report.system.BlackListReport;
import com.seektop.report.system.GlSystemOperationLogEvent;
import com.seektop.report.system.IpProcessReport;
import com.seektop.report.user.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class ReportServiceImpl implements ReportService {

    @Resource
    private UserProducer userProducer;

    @Resource
    private ReportProducer reportProducer;

    @Resource
    private DefaultProducer defaultProducer;

    @Resource
    private BalanceChangeReportService balanceChangeReportService;

    @Override
    public void withdrawPayout(WithdrawMessage withdrawMessage) {
        defaultProducer.withdrawPayout(withdrawMessage);
    }

    @Override
    public void rechargeOrderTimeoutDelayReport(DigitalRechargeOrderTimeoutDelayReport report) {
        reportProducer.digitalRechargeOrderTimeoutDelayReport(report);
    }

    @Override
    public void digitalBalanceAdjustReport(ReportEvent reportEvent, DigitalBalanceAdjustReport report) {
        reportProducer.sendMessage(reportEvent, report);
    }

    @Override
    public void digitalTransferReturnReport(DigitalTransferReturnReport report) {
        reportProducer.sendMessage(ReportEvent.DigitalTransferReturn, report);
    }

    @Override
    public void digitalRechargeReport(DigitalRechargeReport report) {
        reportProducer.sendMessage(ReportEvent.DigitalRecharge, report);
    }

    @Override
    public void digitalWithdrawReport(DigitalWithdrawReport report) {
        reportProducer.sendMessage(ReportEvent.DigitalWithdraw, report);
    }

    @Override
    public void userDigitalAccountCreateReport(UserDigitalAccountCreateReport report) {
        reportProducer.userDigitalAccountCreateReport(report);
    }

    @Override
    public void c2cEggRecordReport(C2CEggRecordReport report) {
        reportProducer.c2cEggRecordReport(report);
    }

    @Override
    public void c2cWithdrawReceiveTimeoutReport(C2CWithdrawReceiveTimeoutReport report) {
        reportProducer.c2cWithdrawReceiveTimeoutReport(report);
    }

    @Override
    public void c2cWithdrawReceiveAlertReport(C2CWithdrawReceiveAlertReport report) {
        reportProducer.c2cWithdrawReceiveAlertReport(report);
    }

    @Override
    public void c2cRechargePaymentTimeoutReport(C2CRechargePaymentTimeoutReport report) {
        reportProducer.c2cRechargePaymentTimeoutReport(report);
    }

    @Override
    public void c2cRechargePaymentAlertReport(C2CRechargePaymentAlertReport report) {
        reportProducer.c2cRechargePaymentAlertReport(report);
    }

    @Override
    public void c2cOrderUnlockReport(C2COrderUnlockReport report) {
        reportProducer.c2cOrderUnlockReport(report);
    }

    @Override
    public void ipProcessReport(IpProcessReport ipProcessReport) {
        defaultProducer.sendAliyunIpParsingReport(ipProcessReport);
    }

    @Override
    public void userSynch(UserSynch userSynch) {
        userProducer.sendMessage(userSynch);
    }

    @Override
    public void loginReport(LoginReport loginReport) {
        reportProducer.sendMessage(ReportEvent.Login, loginReport);
    }

    @Override
    public void registerReport(RegisterReport registerReport) {
        reportProducer.sendMessage(ReportEvent.Register, registerReport);
    }

    @Override
    public void fundReport(FundBaseReport report) {
        report.setTimestamp(System.currentTimeMillis());
        defaultProducer.sendFundReport(report);
    }

    @Override
    public void noticeReport(NoticeReport report) {
        defaultProducer.sendNotice(report);
    }

    @Override
    public void bonusReport(BonusReport report) {
        reportProducer.sendMessage(ReportEvent.Bonus, report);
    }

    @Override
    public void bettingReport(BettingReport report) {
        reportProducer.sendMessage(ReportEvent.Betting, report);
    }

    @Override
    public void shareWalletReport(ShareWalletReport report) {
        reportProducer.sendMessage(ReportEvent.ShareWalletGame, report);
    }

    @Override
    public void GameTransferReport(GameTransferReport report) {
        reportProducer.sendMessage(ReportEvent.Transfer, report);
    }

    @Override
    public void rebateReport(RebateReport report) {
        reportProducer.sendMessage(ReportEvent.Rebate, report);
    }

    @Override
    public void bettingBalanceReport(BettingBalanceReport bbReport) {
        reportProducer.sendMessage(ReportEvent.BettingBalance, bbReport);
    }

    @Override
    public void balanceChangeReport(BalanceChangeReport balanceChangeReport) {
        balanceChangeReport.setUuid(null);
        if (balanceChangeReport.getReallyAmount() == null || balanceChangeReport.getReallyAmount() == 0) {
            return;
        }
        reportProducer.sendMessage(ReportEvent.BalanceChange, balanceChangeReport);
    }

    @Override
    public void rechargeReport(RechargeReport data) {
        try {
            balanceChangeReportService.report(data);
            reportProducer.sendMessage(ReportEvent.Recharge, data);
        } catch (Exception ex) {
            log.error("充值上报异常:", ex);
        }
   }

    @Override
    public void withdrawReport(WithdrawReport report) {
        balanceChangeReportService.report(report);
        reportProducer.sendMessage(ReportEvent.Withdraw, report);
    }

    @Override
    public void parentOrderReport(WithdrawParentOrderReport parentOrderReport) {
        reportProducer.sendMessage(ReportEvent.WithdrawParentOrder, parentOrderReport);
    }

    @Override
    public void balanceTransferReport(BalanceTransferReport report) {
        reportProducer.sendMessage(ReportEvent.BalanceTransfer, report);
    }

    @Override
    public void reportBankDeduction(DeductionCreditReport report) {
        reportProducer.sendMessage(ReportEvent.BankDeductionCredit, report);
    }

    @Override
    public void reportWithdrawReturn(WithdrawReturnReport returnReport) {
        log.info("withdrawReturn:{}", JSON.toJSONString(returnReport));
        reportProducer.sendMessage(ReportEvent.WithdrawReturn, returnReport);
    }

    public void activityManualSendingReport(ActivityBaseReport report) {
        defaultProducer.sendActivity(report);
    }

    @Override
    public void gameUserBalanceReport(GameUserBalanceReport data) {
        reportProducer.sendMessage(ReportEvent.GameUserBalance, data);
    }

    @Override
    public void vipExpReport(VipExpReport vipExpReport) {
        reportProducer.sendMessage(ReportEvent.VipExp, vipExpReport);
    }

    @Override
    public void liveFundRecordReport(LiveFundRecordReport recordReport) {
        defaultProducer.sendLive(recordReport);
    }

    @Override
    public void liveGiftPresentReport(LiveGiftPresentReport report) {
        reportProducer.sendMessage(ReportEvent.LiveGiftPresent, report);
    }

    @Override
    public void reportSubCoin(SubCoinReport report) {
        reportProducer.sendMessage(ReportEvent.SubCoin, report);
    }

    @Override
    public void reportAddSubCoin(AddCoinReport report) {
        reportProducer.sendMessage(ReportEvent.AddCoin, report);
    }

    @Override
    public void reportSubCoinReturn(SubCoinReturnReport report) {
        reportProducer.sendMessage(ReportEvent.SubCoinReturn, report);
    }

    @Override
    public void reportBettingBalance(BettingBalanceReport report) {
        reportProducer.sendMessage(ReportEvent.BettingBalance, report);
    }

    @Override
    public void userOperationLogReport(UserOperationLogReport report) {
        report.setEvent(UserReportEvent.USER_OPERATION_LOG.value());
        report.setTimestamp(System.currentTimeMillis());
        userProducer.sendMessage(report);
    }

    @Override
    public void userBankCardReport(UserBankCardReport report) {
        report.setEvent(UserReportEvent.USER_BANK_CARD.value());
        report.setTimestamp(System.currentTimeMillis());
        userProducer.sendMessage(report);
    }

    @Override
    public void userBalanceReport(UserBalanceReport report) {
        report.setEvent(UserReportEvent.USER_BALANCE.value());
        report.setTimestamp(System.currentTimeMillis());
        userProducer.sendMessage(report);
    }

    @Override
    public void userManageLogReport(UserManageLogReport report) {
        report.setEvent(UserReportEvent.USER_MANAGE_LOG.value());
        report.setTimestamp(System.currentTimeMillis());
        userProducer.sendMessage(report);
    }

    @Override
    public void blacklistSettingReport(BlackListReport report) {
        defaultProducer.sendBlacklistSettingReport(report);
    }

    @Override
    public void commssionReport(CommissionReport report) {
        reportProducer.sendMessage(ReportEvent.Commission, report);
    }

    @Override
    public void balanceRecordReport(BalanceRecordReport recordReport) {
        reportProducer.sendMessage(ReportEvent.BalanceRecord, recordReport);
    }

    @Override
    public void balanceRecordReportV2(BalanceRecordReport recordReport) {
        reportProducer.sendBalanceRecord(recordReport);
    }

    @Override
    public void goldMallBalanceRecordReport(GoldMallBalanceRecordReport goldMallBalanceRecordReport) {
        reportProducer.sendMessage(ReportEvent.GoldMallBalanceRecord, goldMallBalanceRecordReport);
    }

    @Override
    public void upAmountReport(UpAmountReport upAmountReport, UserTypeEnum userType) {
        if (userType == UserTypeEnum.PROXY) {
            reportProducer.sendMessage(ReportEvent.ProxyPayoutRecharge, upAmountReport);
        } else {
            reportProducer.sendMessage(ReportEvent.UpAmount, upAmountReport);
        }
    }

    @Override
    public void proxyTransferOutReport(ProxyTransferOutReport proxyTransferOutReport) {
        reportProducer.sendMessage(ReportEvent.ProxyTransferOut, proxyTransferOutReport);
    }

    @Override
    public void proxyTransferInReport(ProxyTransferInReport proxyTransferInReport) {
        reportProducer.sendMessage(ReportEvent.ProxyTransferIn, proxyTransferInReport);
    }

    @Override
    public void proxyTransferReturnReport(ProxyTransferReturnReport proxyTransferReturnReport) {
        reportProducer.sendMessage(ReportEvent.ProxyTransferReturn, proxyTransferReturnReport);
    }

    @Override
    public void proxyRechargeRebateReport(ProxyRechargeRebateReport proxyRechargeRebateReport) {
        reportProducer.sendMessage(ReportEvent.ProxyRechargeRebateRebate, proxyRechargeRebateReport);
    }

    @Override
    public void systemOperationReport(GlSystemOperationLogEvent logEvent) {
        defaultProducer.systemOperationReport(logEvent);
    }

    @Override
    public void livePlazaInformationGrabReport(LivePlazaInformationGrabReport plazaInformationGrabReport) {
        defaultProducer.livePlazaInformationGrab(plazaInformationGrabReport);
    }

    @Override
    public void rechargeEffectBetReport(String orderId) {
        reportProducer.rechargeEffectBetReport(new RechargeEffectBetReport(orderId));
    }

}