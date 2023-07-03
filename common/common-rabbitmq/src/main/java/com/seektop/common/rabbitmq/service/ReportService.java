package com.seektop.common.rabbitmq.service;

import com.seektop.enumerate.ReportEvent;
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

public interface ReportService {

    /**
     * 提现发送出款信息
     *
     * @param withdrawMessage
     */
    void withdrawPayout(WithdrawMessage withdrawMessage);

    /**
     * 充值订单超时延时上报
     *
     * @param report
     */
    void rechargeOrderTimeoutDelayReport(DigitalRechargeOrderTimeoutDelayReport report);

    /**
     * 资金调整数据上报
     *
     * @param reportEvent
     * @param report
     */
    void digitalBalanceAdjustReport(ReportEvent reportEvent, DigitalBalanceAdjustReport report);

    /**
     * 数字货币转账失败退回上报
     *
     * @param report
     */
    void digitalTransferReturnReport(DigitalTransferReturnReport report);

    /**
     * 数字货币充币记录上报
     *
     * @param report
     */
    void digitalRechargeReport(DigitalRechargeReport report);

    /**
     * 数字货币提现记录上报
     *
     * @param report
     */
    void digitalWithdrawReport(DigitalWithdrawReport report);

    /**
     * 用户数字货币账户创建上报
     *
     * @param report
     */
    void userDigitalAccountCreateReport(UserDigitalAccountCreateReport report);

    /**
     * C2C彩蛋倒计时上报
     *
     * @param report
     */
    void c2cEggRecordReport(C2CEggRecordReport report);

    /**
     * C2C提现订单收款超时上报
     *
     * @param report
     */
    void c2cWithdrawReceiveTimeoutReport(C2CWithdrawReceiveTimeoutReport report);

    /**
     * C2C提现订单收款提醒上报
     *
     * @param report
     */
    void c2cWithdrawReceiveAlertReport(C2CWithdrawReceiveAlertReport report);

    /**
     * C2C充值订单付款超时上报
     *
     * @param report
     */
    void c2cRechargePaymentTimeoutReport(C2CRechargePaymentTimeoutReport report);

    /**
     * C2C充值订单付款提醒上报
     *
     * @param report
     */
    void c2cRechargePaymentAlertReport(C2CRechargePaymentAlertReport report);

    /**
     * C2C撮合订单延时解锁上报
     *
     * @param report
     */
    void c2cOrderUnlockReport(C2COrderUnlockReport report);

    /**
     * IP解析数据上报
     *
     * @param ipProcessReport
     */
    void ipProcessReport(IpProcessReport ipProcessReport);

    /**
     * 用户信息同步上报
     *
     * @param userSynch
     */
    void userSynch(UserSynch userSynch);

    /**
     * 登录上报
     *
     * @param loginReport
     */
    void loginReport(LoginReport loginReport);

    /**
     * 注册上报
     *
     * @param registerReport
     */
    void registerReport(RegisterReport registerReport);

    /**
     * 财务相关上报
     *
     * @param report
     */
    void fundReport(FundBaseReport report);

    /**
     * 通知上报
     *
     * @param report
     */
    void noticeReport(NoticeReport report);

    /**
     * 红利相关上报
     *
     * @param report
     */
    void bonusReport(BonusReport report);

    /**
     * 游戏注单上报
     * @param report
     */
    void bettingReport(BettingReport report);

    /**
     * 共享钱包账变上报
     * @param report
     */
    void shareWalletReport(ShareWalletReport report);

    /**
     * 游戏转账上报
     *
     * @param report
     */
    void GameTransferReport(GameTransferReport report);

    /**
     * 返水相关上报
     *
     * @param report
     */
    void rebateReport(RebateReport report);

    /**
     * 资金流水上报
     *
     * @param bbReport
     */
    void bettingBalanceReport(BettingBalanceReport bbReport);

    void balanceChangeReport(BalanceChangeReport balanceChangeReport);

    void rechargeReport(RechargeReport data);

    void withdrawReport(WithdrawReport report);

    void parentOrderReport(WithdrawParentOrderReport parentOrderReport);

    /**
     * 转账上报
     *
     * @param report
     */
    void balanceTransferReport(BalanceTransferReport report);

    /**
     * 银行卡充值清算
     *
     * @param report
     */
    void reportBankDeduction(DeductionCreditReport report);

    /**
     * VIP经验值上报
     *
     * @param vipExpReport
     */
    void vipExpReport(VipExpReport vipExpReport);


    /**
     * 提现退回上报
     *
     * @param returnReport
     */
    void reportWithdrawReturn(WithdrawReturnReport returnReport);

    /**
     * 活動手動發放
     *
     * @param report
     */
    void activityManualSendingReport(ActivityBaseReport report);

    /**
     * 游戏余额更新上报
     */
    void gameUserBalanceReport(GameUserBalanceReport data);

    /**
     * 直播财务记录上报
     *
     * @param recordReport
     */
    void liveFundRecordReport(LiveFundRecordReport recordReport);

    /**
     * 礼物赠送记录上报
     *
     * @param report
     */
    void liveGiftPresentReport(LiveGiftPresentReport report);

    /**
     * 减币上报
     *
     * @param report
     */
    void reportSubCoin(SubCoinReport report);

    /**
     * 加币上报
     *
     * @param report
     */
    void reportAddSubCoin(AddCoinReport report);

    /**
     * 减币退回
     *
     * @param report
     */
    void reportSubCoinReturn(SubCoinReturnReport report);

    /**
     * 流水详情上报
     *
     * @param report
     */
    void reportBettingBalance(BettingBalanceReport report);

    /**
     * 用户操作记录上报
     *
     * @param report
     */
    void userOperationLogReport(UserOperationLogReport report);

    /**
     * 用户绑定银行卡上报
     *
     * @param report
     */
    void userBankCardReport(UserBankCardReport report);

    /**
     * 用户账户余额上报
     *
     * @param report
     */
    void userBalanceReport(UserBalanceReport report);

    /**
     * 用户管理端操作记录
     *
     * @param report
     */
    void userManageLogReport(UserManageLogReport report);

    /**
     * 黑名单设置上报
     *
     * @param report
     */
    void blacklistSettingReport(BlackListReport report);

    /**
     * 代理返佣上报
     */
    void commssionReport(CommissionReport commissionReport);

    /**
     * 资金明细上报
     */
    void balanceRecordReport(BalanceRecordReport recordReport);

    /**
     * 资金明细上报V2
     * 通过资金明细的Exchange和RoutingKey
     *
     * @param recordReport
     */
    void balanceRecordReportV2(BalanceRecordReport recordReport);

    /**
     * 金币明细上报
     */
    void goldMallBalanceRecordReport(GoldMallBalanceRecordReport goldMallBalanceRecordReport);

    /**
     * 代充资金明细上报
     */
    void upAmountReport(UpAmountReport upAmountReport, UserTypeEnum userType);

    void proxyTransferOutReport(ProxyTransferOutReport proxyTransferOutReport);

    void proxyTransferInReport(ProxyTransferInReport proxyTransferInReport);

    void proxyTransferReturnReport(ProxyTransferReturnReport proxyTransferReturnReport);

    void proxyRechargeRebateReport(ProxyRechargeRebateReport proxyRechargeRebateReport);

    /**
     * 后台操作日志上报
     * @param logEvent
     */
    void systemOperationReport(GlSystemOperationLogEvent logEvent);

    /**
     * 广场资讯爬虫入库
     * @param plazaInformationGrabReport
     */
    void livePlazaInformationGrabReport(LivePlazaInformationGrabReport plazaInformationGrabReport);

    /**
     * 充值成功提现流水处理上报
     *
     * @param orderId
     */
    void rechargeEffectBetReport(String orderId);

}