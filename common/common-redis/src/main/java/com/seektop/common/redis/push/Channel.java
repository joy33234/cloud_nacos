package com.seektop.common.redis.push;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Channel {

    Test(400),
    RechargeSuccessMsg(401),//会员充值成功
    WithdrawSuccessMsg(402),//会员提现成功
    GameBalanceChange(403),//用户游戏余额变更
    PTPrizePoolChange(404),//PT奖池金额变更，弃用，已经不再同步数据
    UpAmountSuccess(405),//上分成功
    TransferFailed(406),//转账失败，一定程度使用
    RechargeFailMsg(407),//会员充值失败
    Dividend(408),//红利
    Rebate(409),//返水
    WithdrawalReturn(410),//提现退回成功
    Bonus(411),//加币
    Deduction(412),//减币
    WithdrawFail(413),//提现失败
    WithdrawalReturnFail(414),//提现退回失败
    AGPrizePoolChange(415),//AG奖池金额变更，弃用，已经不再同步数据
    UserLoginLocked(416),//用户登录锁定
    MWPrizePoolChange(418),//PT奖池金额变更，弃用，已经不再同步数据
    PPPrizePoolChange(417),//PP奖池金额变更，弃用，已经不再同步数据
    awardIssue(420),//奖品发货
    awardRefuse(421),//奖品发货拒绝
    awardObtain(422), //获得奖品
    userBindCardApprove(423), // 用户申请人工绑卡审批
    PlatformMaintain(425), // 平台维护信息
    BankRechargeAct(426); //银行卡充值活动

    private int value;

    Channel(int value) {
        this.value = value;
    }

    @JsonValue
    public int value() {
        return this.value;
    }
}
