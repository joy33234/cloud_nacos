package com.seektop.fund.handler;

import com.alibaba.fastjson.JSON;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.NumStringUtils;
import com.seektop.constant.user.UserConstant;
import com.seektop.data.param.betting.FindBettingCommParamDO;
import com.seektop.data.result.betting.SimpleBettingBalance;
import com.seektop.data.service.BettingBalanceService;
import com.seektop.data.service.BettingService;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.fund.BettingBalanceEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlWithdrawEffectBetBusiness;
import com.seektop.fund.controller.backend.dto.withdraw.EffectAdjustDto;
import com.seektop.fund.controller.backend.dto.withdraw.EffectCleanDto;
import com.seektop.fund.controller.backend.dto.withdraw.EffectRecoverDto;
import com.seektop.fund.controller.backend.dto.withdraw.EffectRemoveDto;
import com.seektop.fund.dto.result.withdraw.GlWithdrawEffectBetDO;
import com.seektop.fund.model.GlWithdrawEffectBet;
import com.seektop.report.fund.BettingBalanceReport;
import com.seektop.user.dto.GlUserManageDO;
import com.seektop.user.service.GlUserService;
import com.seektop.user.service.UserManageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * 用户资金流水
 */
@Slf4j
@Component
public class UserWithdrawEffectHandler {

    @Reference(retries = 2, timeout = 3000)
    private UserManageService userManageService;
    @Reference(retries = 2, timeout = 3000)
    private GlUserService userService;
    @Reference(retries = 2, timeout = 3000)
    private BettingService bettingService;
    @Reference(retries = 2, timeout = 3000)
    private BettingBalanceService bettingBalanceService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private GlWithdrawEffectBetBusiness glWithdrawEffectBetBusiness;
    @Resource
    private RedisService redisService;

    /**
     * 提现成功后上报流水变动数据
     *
     * @param userId
     * @param orderId
     * @param amount
     */
    public void reportWithdrawBettingBalance(Integer userId, String orderId, BigDecimal amount,String coin) throws GlobalException {
        RPCResponse<GlUserDO> rpcResponse = userService.findById(userId);
        if (RPCResponseUtils.isFail(rpcResponse)) {
            log.error("查询用户{}的信息失败", userId, rpcResponse.getMessage());
            return;
        }
        Date nowDate = new Date();
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userId, coin);
        // 目前用户当前已完成提现流水
        FindBettingCommParamDO findBettingCommParamDO = new FindBettingCommParamDO();
        findBettingCommParamDO.setUserId(userId);
        findBettingCommParamDO.setStartTime(effectBet.getEffectStartTime() == null ? new Date().getTime() :effectBet.getEffectStartTime().getTime());
        findBettingCommParamDO.setEndTime(nowDate.getTime());
        findBettingCommParamDO.setGamePlatformIds(new ArrayList<>());
        RPCResponse<BigDecimal> totalBet = bettingService.sumBettingEffectiveAmount(findBettingCommParamDO);
        if (RPCResponseUtils.isFail(totalBet)) {
            log.error("获取用户当前已完成流水发生异常", totalBet.getMessage());
            return;
        }
        // 上报内容组装
        BettingBalanceReport report = new BettingBalanceReport();
        report.setOrderId(orderId);
        report.setType(BettingBalanceEnum.WITHDRAW.getCode());
        report.setSubType(1);
        report.setAmount(amount.multiply(BigDecimal.valueOf(100000000)));
        report.setMagnificationFactor(BigDecimal.ZERO);
        report.setSingleBettingDesire(BigDecimal.ZERO);
        report.setBetEffect(totalBet.getData().multiply(BigDecimal.valueOf(100000000)));
        report.setTotalBettingDesire(BigDecimal.ZERO);
        report.setLeftBettingDesireBefore(effectBet.getRequiredBet() == null ? BigDecimal.ZERO:effectBet.getRequiredBet().multiply(BigDecimal.valueOf(100000000)));
        report.setLeftBettingDesireAfter(BigDecimal.ZERO);
        report.setOperator("admin");
        report.setUid(userId);
        report.setIsFake(rpcResponse.getData().getIsFake());
        report.setFinishTime(nowDate);
        reportService.reportBettingBalance(report);
    }

    /**
     * 用户资金流水调整
     *
     * @param effectAdjustDto
     * @param admin
     * @throws GlobalException
     */
    public void adjust(EffectAdjustDto effectAdjustDto, GlAdminDO admin) throws GlobalException {
        String coinCode = effectAdjustDto.getCoinCode();
        Integer userId = effectAdjustDto.getUserId();
        Integer count = RPCResponseUtils.getData(userManageService.findRecordUnderChecking(userId));
        if (count > 0) {
            throw new GlobalException("有审核中的资金流水调整记录，不能重复申请");
        }

        //目标必须是会员
        GlUserDO user = RPCResponseUtils.getData(userService.findById(userId));
        if (user.getUserType() != 0) {
            throw new GlobalException("只能给会员账户调整流水");
        }
        //用户提现流水信息
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userId, effectAdjustDto.getCoinCode());
        BigDecimal amount = effectAdjustDto.getAmount();

        //如果超过调整额度，那么以最大可调整的额度为准
        if (effectBet.getRequiredBet().add(amount).compareTo(BigDecimal.ZERO) == -1) {
            amount = effectBet.getRequiredBet().negate();
        }

        //操作人
        Date now = new Date();
        GlUserManageDO manage = new GlUserManageDO();
        manage.setCreateTime(now);
        manage.setCreator(admin.getUsername());
        manage.setOptDesc(UserConstant.UserOperateType.BETTING_BALANCE_ADJUSTMENT.getDesc());
        manage.setOptType(UserConstant.UserOperateType.BETTING_BALANCE_ADJUSTMENT.getOptType());
        manage.setStatus(0);
        manage.setUserId(user.getId());
        manage.setUsername(user.getUsername());
        manage.setUserType(user.getUserType());
        manage.setOptData(amount.toString());
        manage.setCoin(coinCode);

        BigDecimal freezeBalanceBefore = effectBet.getRequiredBet();

        BigDecimal beforeData = freezeBalanceBefore;
        BigDecimal afterData = effectBet.getRequiredBet().add(amount);

        manage.setOptBeforeData("剩余流水:" + (beforeData.intValue() > 0 ? NumStringUtils.formatDecimal(beforeData) : "0.00")+ coinCode);
        manage.setOptAfterData("剩余流水:" + (afterData.intValue() > 0 ? NumStringUtils.formatDecimal(afterData) : "0.00")+ coinCode);

        manage.setRemark(effectAdjustDto.getRemark());
        userManageService.saveManage(manage);
    }

    /**
     * 用户资金流水清除
     *
     * @param cleanDto
     * @param admin
     * @throws GlobalException
     */
    public void remove(EffectRemoveDto cleanDto, GlAdminDO admin) throws GlobalException {
        String orderNo = cleanDto.getOrderNo();
        String coinCode = cleanDto.getCoinCode();
        //确认记录状态，是否能进行恢复操作
        RPCResponse<SimpleBettingBalance> response = bettingBalanceService.findByOrderId(orderNo);
        SimpleBettingBalance balance = RPCResponseUtils.getData(response);
        if (ObjectUtils.isEmpty(balance) || balance.getStatus() == 0) {
            throw new GlobalException("无法进行清除操作");
        }
        //获取记录对应用户
        GlUserDO user = RPCResponseUtils.getData(userService.findById(balance.getUid()));
        if (ObjectUtils.isEmpty(user)) {
            throw new GlobalException("无法进行清除操作");
        }

        //准备数据
        BigDecimal amount = balance.getSingleBettingDesire();
        Integer userId = user.getId();
        String username = user.getUsername();

        Integer count = RPCResponseUtils.getData(userManageService.findRecordUnderChecking(userId));
        if (count > 0) {
            throw new GlobalException("有审核中的资金流水调整记录，不能重复申请");
        }

        //用户提现流水信息
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userId, cleanDto.getCoinCode());

        if (effectBet.getRequiredBet().subtract(amount).compareTo(BigDecimal.ZERO) == -1) {
            throw new GlobalException("流水不可以调整为负数");
        }

        //提交审核
        Date now = new Date();
        GlUserManageDO manage = new GlUserManageDO();
        manage.setCreateTime(now);
        manage.setCreator(admin.getUsername());
        manage.setOptDesc(UserConstant.UserOperateType.BETTING_BALANCE_REMOVE.getDesc());
        manage.setOptType(UserConstant.UserOperateType.BETTING_BALANCE_REMOVE.getOptType());
        manage.setStatus(0);
        manage.setUserId(userId);
        manage.setUsername(username);
        manage.setUserType(0);
        manage.setOptData(amount.toString() + "," + orderNo);
        manage.setRemark(cleanDto.getRemark());
        manage.setCoin(cleanDto.getCoinCode());

        //先前总流水需求
        BigDecimal revTargetFreezeBalanceBefore = effectBet.getRequiredBet();

        //目前的有效流水
        FindBettingCommParamDO param = new FindBettingCommParamDO();
        param.setUserId(userId);
        param.setStartTime(effectBet.getEffectStartTime().getTime());
        param.setEndTime(now.getTime());
        param.setCoinCode(cleanDto.getCoinCode());
        BigDecimal validBalanceRev = RPCResponseUtils.getData(bettingService.sumBettingEffectiveAmount(param));

        BigDecimal beforeData = revTargetFreezeBalanceBefore.subtract(validBalanceRev);
        BigDecimal afterData = (revTargetFreezeBalanceBefore.subtract(validBalanceRev.add(amount)));
        manage.setOptBeforeData("剩余流水:" + (beforeData.intValue() > 0 ? NumStringUtils.formatDecimal(beforeData) : "0.00")+coinCode);
        manage.setOptAfterData("剩余流水:" + (afterData.intValue() > 0 ? NumStringUtils.formatDecimal(afterData) : "0.00")+coinCode);
        userManageService.saveManage(manage);

        //重新上报之前的记录
        log.info("updateRecord orderNo = {}, uuid = {}, status = {}", orderNo, balance.getUuid(), 2);
        updateRecord(balance.getUuid(), balance.getTimestamp(), 2);
    }

    /**
     * 用户资金流水恢复
     *
     * @param recoverDto
     * @param admin
     * @throws GlobalException
     */
    public void recover(EffectRecoverDto recoverDto, GlAdminDO admin) throws GlobalException {
        String orderNo = recoverDto.getOrderNo();
        String coinCode = recoverDto.getCoinCode();
        //确认记录状态，是否能进行恢复操作
        RPCResponse<SimpleBettingBalance> response = bettingBalanceService.findByOrderId(orderNo);
        SimpleBettingBalance balance = RPCResponseUtils.getData(response);
        if (ObjectUtils.isEmpty(balance) || balance.getStatus().intValue() == 1) {
            throw new GlobalException("无法进行恢复操作");
        }
        //获取记录对应用户
        GlUserDO user = RPCResponseUtils.getData(userService.findById(balance.getUid()));
        if (ObjectUtils.isEmpty(user)) {
            throw new GlobalException("无法进行清除操作");
        }

        BigDecimal amount = balance.getSingleBettingDesire();
        Integer userId = user.getId();

        Integer count = RPCResponseUtils.getData(userManageService.findRecordUnderChecking(userId));
        if (count > 0) {
            throw new GlobalException("有审核中的资金流水调整记录，不能重复申请");
        }

        //用户提现流水信息
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userId,recoverDto.getCoinCode());
        //提交审核
        Date now = new Date();
        GlUserManageDO manage = new GlUserManageDO();
        manage.setCreateTime(now);
        manage.setCreator(admin.getUsername());
        manage.setOptDesc(UserConstant.UserOperateType.BETTING_BALANCE_RECOVER.getDesc());
        manage.setOptType(UserConstant.UserOperateType.BETTING_BALANCE_RECOVER.getOptType());
        manage.setStatus(0);
        manage.setUserId(userId);
        manage.setUsername(user.getUsername());
        manage.setUserType(0);
        manage.setOptData(amount.toString() + "," + orderNo);
        manage.setRemark(recoverDto.getRemark());
        manage.setCoin(recoverDto.getCoinCode());

        //先前总流水需求
        BigDecimal revTargetFreezeBalanceBefore = effectBet.getRequiredBet();

        //目前的有效流水
        FindBettingCommParamDO param = new FindBettingCommParamDO();
        param.setUserId(userId);
        param.setStartTime(effectBet.getEffectStartTime().getTime());
        param.setEndTime(now.getTime());
        param.setCoinCode(recoverDto.getCoinCode());
        BigDecimal validBalanceRev = RPCResponseUtils.getData(bettingService.sumBettingEffectiveAmount(param));

        BigDecimal beforeData = revTargetFreezeBalanceBefore.subtract(validBalanceRev);
        BigDecimal afterData = (effectBet.getRequiredBet().subtract(validBalanceRev)).add(amount);

        manage.setOptBeforeData("剩余流水:" + (beforeData.intValue() > 0 ? NumStringUtils.formatDecimal(beforeData) : "0.00")+coinCode);
        manage.setOptAfterData("剩余流水:" + (afterData.intValue() > 0 ? NumStringUtils.formatDecimal(afterData) : "0.00")+coinCode);
        userManageService.saveManage(manage);

        //重新上报先前的记录
        log.info("updateRecord orderNo = {}, uuid = {}, status = {}", orderNo, balance.getUuid(), 3);
        updateRecord(balance.getUuid(), balance.getTimestamp(), 3);
    }

    /**
     * 指定单号记录的状态
     *
     * @param uuid
     * @param status 更改之后的状态，可以是0已清除，1正常，2清除审核中，3恢复审核中
     */
    private void updateRecord(String uuid, Long timestamp, Integer status) {
        BettingBalanceReport report = new BettingBalanceReport();
        report.setUuid(uuid);
        report.setStatus(status);
        report.setTimestamp(new Date(timestamp));
        reportService.bettingBalanceReport(report);
    }

    /**
     * 流水清零审核
     * 1.查询中心账户
     * 2.数据库查询用户的流水记录数据
     * 3.上报资金流水  提取出来？
     *
     * @param userDO
     * @param remark
     * @param operator
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void cleanWithdrawEffect(GlUserDO userDO, String remark, String operator,String coin) throws GlobalException {
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userDO.getId(), coin);
        Date now = new Date();

        //目前的有效流水
        FindBettingCommParamDO param = new FindBettingCommParamDO();
        param.setUserId(userDO.getId());
        param.setStartTime(effectBet.getEffectStartTime().getTime());
        param.setEndTime(now.getTime());
        param.setGamePlatformIds(new ArrayList<>());
        BigDecimal effectBet_reset = RPCResponseUtils.getData(bettingService.sumBettingEffectiveAmount(param));

        GlWithdrawEffectBetDO cleanBet = new GlWithdrawEffectBetDO();
        cleanBet.setUserId(userDO.getId());
        cleanBet.setChangeDate(now);
        cleanBet.setEffectAmount(BigDecimal.ZERO);
        cleanBet.setAmount(BigDecimal.ZERO);
        cleanBet.setIsClean(true);
        cleanBet.setCoin(coin);
        glWithdrawEffectBetBusiness.syncWithdrawEffect(cleanBet);

        //上报资金流水
        BettingBalanceReport resetBettingReport = new BettingBalanceReport();
        resetBettingReport.setUid(userDO.getId());
        resetBettingReport.setOrderId(getAdjustmentNo("RESET"));
        resetBettingReport.setType(13);
        //当前已完成流水
        resetBettingReport.setBetEffect(effectBet_reset.multiply(new BigDecimal(100000000)));
        //当前单笔流水需求
        resetBettingReport.setSingleBettingDesire(BigDecimal.ZERO);
        //当前流水总需求
        resetBettingReport.setTotalBettingDesire(BigDecimal.ZERO);
        //之前的所需流水
        resetBettingReport.setLeftBettingDesireBefore(effectBet.getRequiredBet().multiply(new BigDecimal(100000000)));
        //之后的所需流水
        resetBettingReport.setLeftBettingDesireAfter(BigDecimal.ZERO);
        //真实/虚拟账户
        resetBettingReport.setOperator(operator);
        resetBettingReport.setIsFake(userDO.getIsFake());
        resetBettingReport.setRemark(remark);
        resetBettingReport.setFinishTime(now);
        resetBettingReport.setCoin(coin);
        reportService.reportBettingBalance(resetBettingReport);
    }

    private String getAdjustmentNo(String prefix) throws GlobalException {
        try {
            Date now = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
            StringBuilder tradeNo = new StringBuilder();
            tradeNo.append(prefix);
            tradeNo.append(sdf.format(now));
            tradeNo.append(NumStringUtils.numToSixUpperString(RandomUtils.nextInt(100000, 999999)));
            return tradeNo.toString();
        } catch (Exception e) {
            log.error("getAdjustmentNo error", e);
            throw new GlobalException(e.getMessage(), e);
        }
    }

    /**
     * 清除指定订单的流水 二审
     * 如果流水为0，isClean = true, changeDate = now, 以及所需流水.否则，isClean = false, changeDate = 不变
     *
     * @param revAmount
     * @param remark
     * @param operator
     * @param orderNo
     * @param userDO
     * @throws GlobalException
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void bettingBalanceRemove(BigDecimal revAmount, String remark, String operator, String orderNo, GlUserDO userDO,String coin) throws GlobalException {
        //用户提现流水信息
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userDO.getId(), coin);
        Date now = new Date();
        //目前的有效流水
        FindBettingCommParamDO param = new FindBettingCommParamDO();
        param.setUserId(userDO.getId());
        param.setStartTime(effectBet.getEffectStartTime().getTime());
        param.setEndTime(now.getTime());
        BigDecimal userEffectBet = RPCResponseUtils.getData(bettingService.sumBettingEffectiveAmount(param));
        //先前总流水需求
        BigDecimal rev_fre_bal_bre = effectBet.getRequiredBet();
        //减去订单对应的流水
        BigDecimal rev_bre_bal = rev_fre_bal_bre.subtract(revAmount);
        // 清除流水之后,用户所需流水小于0
        if (rev_bre_bal.compareTo(BigDecimal.ZERO) != 1) {
            rev_bre_bal = BigDecimal.ZERO;
        }
        GlWithdrawEffectBetDO newBet = new GlWithdrawEffectBetDO();
        newBet.setUserId(userDO.getId());
        newBet.setIsClean(false);
        newBet.setChangeDate(effectBet.getEffectStartTime());
        newBet.setEffectAmount(rev_bre_bal);
        newBet.setAmount(BigDecimal.ZERO);
        newBet.setCoin(coin);

        //剩余流水小于等于0，更新流水计算周期
        if (rev_bre_bal.subtract(userEffectBet).compareTo(BigDecimal.ZERO) != 1) {
            rev_bre_bal = BigDecimal.ZERO;
            newBet.setIsClean(true);
            newBet.setEffectAmount(BigDecimal.ZERO);
            newBet.setChangeDate(now);
        }
        glWithdrawEffectBetBusiness.syncWithdrawEffect(newBet);

        //上报资金流水
        BettingBalanceReport revReport = new BettingBalanceReport();
        revReport.setUid(userDO.getId());
        revReport.setOrderId("REV" + orderNo);
        revReport.setType(8);
        //当前已完成流水
        revReport.setBetEffect(userEffectBet.multiply(new BigDecimal(100000000)));
        //当前单笔流水需求
        revReport.setSingleBettingDesire(revAmount.multiply(new BigDecimal(100000000)).negate());//*倍数*10000
        //当前流水总需求
        revReport.setTotalBettingDesire(rev_bre_bal.multiply(new BigDecimal(100000000)));
        //之前的剩余流水需求
        revReport.setLeftBettingDesireBefore(rev_fre_bal_bre.multiply(new BigDecimal(100000000)));
        //之后的剩余流水需求
        revReport.setLeftBettingDesireAfter(rev_bre_bal.multiply(new BigDecimal(100000000)));
        //真实/虚拟账户
        revReport.setOperator(operator);
        revReport.setIsFake(userDO.getIsFake());
        revReport.setRemark(remark);
        revReport.setFinishTime(now);
        revReport.setCoin(coin);
        reportService.reportBettingBalance(revReport);

        //修改先前资金流水的状态，重新上报先前的记录
        updateRecordStatusByOrderId(orderNo, 0, null);
    }

    /**
     * 恢复目标用户流水 二审
     *
     * @param revAmount
     * @param remark
     * @param operator
     * @param orderNo
     * @param userDO
     * @throws GlobalException
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void bettingBalanceRecover(BigDecimal revAmount, String remark, String operator, String orderNo, GlUserDO userDO,String coin) throws GlobalException {
        //用户提现流水信息
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userDO.getId(), coin);
        Date now = new Date();

        //目前的有效流水
        FindBettingCommParamDO param = new FindBettingCommParamDO();
        param.setUserId(userDO.getId());
        param.setStartTime(effectBet.getEffectStartTime().getTime());
        param.setEndTime(now.getTime());
        BigDecimal userEffectBet = RPCResponseUtils.getData(bettingService.sumBettingEffectiveAmount(param));

        //先前总流水需求
        BigDecimal rev_fre_bal_bre = effectBet.getRequiredBet();
        //note:这里和清除订单流水相反
        BigDecimal rev_bre_bal = rev_fre_bal_bre.add(revAmount);
        if (rev_bre_bal.compareTo(BigDecimal.ZERO) != 1) {
            rev_bre_bal = BigDecimal.ZERO;
        }

        GlWithdrawEffectBetDO rev_eff_bet_vo = new GlWithdrawEffectBetDO();
        rev_eff_bet_vo.setUserId(userDO.getId());
        rev_eff_bet_vo.setIsClean(false);
        rev_eff_bet_vo.setAmount(BigDecimal.ZERO);
        rev_eff_bet_vo.setEffectAmount(rev_bre_bal);
        rev_eff_bet_vo.setChangeDate(effectBet.getEffectStartTime());
        rev_eff_bet_vo.setCoin(coin);

        //如果流水已经够了，需要设置清零标记，更新时间和状态
        if (rev_fre_bal_bre.subtract(userEffectBet).compareTo(BigDecimal.ZERO) != 1) {
            rev_eff_bet_vo.setIsClean(true);
            rev_eff_bet_vo.setChangeDate(now);
            if (revAmount.compareTo(BigDecimal.ZERO) == 1) {
                rev_eff_bet_vo.setEffectAmount(revAmount);
            } else {
                rev_eff_bet_vo.setEffectAmount(BigDecimal.ZERO);
            }
        }
        glWithdrawEffectBetBusiness.syncWithdrawEffect(rev_eff_bet_vo);

        //上报资金流水
        BettingBalanceReport recoverReport = new BettingBalanceReport();
        recoverReport.setUid(userDO.getId());
        recoverReport.setOrderId("REC" + orderNo);
        recoverReport.setType(9);
        //当前已完成流水
        recoverReport.setBetEffect(userEffectBet.multiply(new BigDecimal(100000000)));
        //当前单笔流水需求
        recoverReport.setSingleBettingDesire(revAmount.multiply(new BigDecimal(100000000)));//*倍数*10000
        //当前流水总需求
        recoverReport.setTotalBettingDesire(rev_eff_bet_vo.getEffectAmount().multiply(new BigDecimal(100000000)));
        //之前的剩余流水需求
        recoverReport.setLeftBettingDesireBefore(rev_fre_bal_bre.multiply(new BigDecimal(100000000)));
        //之后的剩余流水需求
        recoverReport.setLeftBettingDesireAfter(rev_eff_bet_vo.getEffectAmount().multiply(new BigDecimal(100000000)));
        //真实/虚拟账户
        recoverReport.setOperator(operator);
        recoverReport.setIsFake(userDO.getIsFake());
        recoverReport.setRemark(remark);
        recoverReport.setFinishTime(now);
        reportService.reportBettingBalance(recoverReport);

        //修改先前资金流水的状态，重新上报先前的记录
        updateRecordStatusByOrderId(orderNo, 1, null);

    }

    public void updateRecordStatusByOrderId(String orderId, Integer status, String remark) {
        String id = "";
        BettingBalanceReport recReport = new BettingBalanceReport();
        recReport.setStatus(status);
        try {
            SimpleBettingBalance bettingBalance = RPCResponseUtils.getData(bettingBalanceService.findByOrderId(orderId));
            log.info("updateRecordStatusByOrderId orderId = {}, bettingBalance= {}", orderId, bettingBalance);
            if (bettingBalance != null) {
                id = bettingBalance.getUuid();
                recReport.setTimestamp(new Date(bettingBalance.getTimestamp()));
            } else {
                recReport.setTimestamp(new Date());
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            //使用默认值继续执行
            recReport.setTimestamp(new Date());
        }

        recReport.setUuid(id);
        if (StringUtils.isNotEmpty(remark)) {
            recReport.setRemark(remark);
        }
        log.info("recReport = {}", recReport);
        reportService.reportBettingBalance(recReport);
    }

    @Transactional(rollbackFor = GlobalException.class)
    public void adjustBetBalanceApprove(BigDecimal amount, String remark, String operator, GlUserDO userDO,String coinCode) throws GlobalException {

        //用户中心钱包信息
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userDO.getId(),coinCode);
        Date now = new Date();

        //目前的有效流水
        FindBettingCommParamDO param = new FindBettingCommParamDO();
        param.setUserId(userDO.getId());
        param.setStartTime(effectBet.getEffectStartTime().getTime());
        param.setEndTime(now.getTime());
        param.setCoinCode(coinCode);
        BigDecimal userEffectBet = RPCResponseUtils.getData(bettingService.sumBettingEffectiveAmount(param));

        //先前总流水需求
        BigDecimal requiredBet = effectBet.getRequiredBet();
        //调整之后的流水
        BigDecimal afterAdjust = null;

        //调整时剩余流水
        BigDecimal leftFreezeBalance = requiredBet.subtract(userEffectBet).compareTo(BigDecimal.ZERO) > 0 ? requiredBet.subtract(userEffectBet) : BigDecimal.ZERO;

        GlWithdrawEffectBetDO withdrawEffctBetDO = new GlWithdrawEffectBetDO();
        withdrawEffctBetDO.setUserId(userDO.getId());
        withdrawEffctBetDO.setIsClean(false);
        withdrawEffctBetDO.setAmount(BigDecimal.ZERO);
        withdrawEffctBetDO.setCoin(coinCode);

        if (amount.compareTo(BigDecimal.ZERO) > 0) {//增加流水
            if (leftFreezeBalance.compareTo(BigDecimal.ZERO) != 1) { //剩余流水 < 0 : 超出部分无效
                afterAdjust = amount;

                withdrawEffctBetDO.setChangeDate(now);
                withdrawEffctBetDO.setEffectAmount(amount);
            } else {
                afterAdjust = requiredBet.add(amount);
                withdrawEffctBetDO.setChangeDate(effectBet.getEffectStartTime());
                withdrawEffctBetDO.setEffectAmount(afterAdjust);
            }
        } else {//减少流水 changeRequest.getFreezeAmount() 数据库存储为负数  使用 .add()
            if (requiredBet.add(amount).compareTo(BigDecimal.ZERO) < 0) {
                afterAdjust = BigDecimal.ZERO;

                withdrawEffctBetDO.setChangeDate(now);
                withdrawEffctBetDO.setEffectAmount(BigDecimal.ZERO);
                withdrawEffctBetDO.setIsClean(true);
            } else {
                afterAdjust = requiredBet.add(amount);

                withdrawEffctBetDO.setChangeDate(effectBet.getEffectStartTime());
                withdrawEffctBetDO.setEffectAmount(afterAdjust);
            }
        }
        glWithdrawEffectBetBusiness.syncWithdrawEffect(withdrawEffctBetDO);

        //上报资金流水
        BettingBalanceReport bettingBalanceReport = new BettingBalanceReport();
        bettingBalanceReport.setUid(userDO.getId());
        bettingBalanceReport.setOrderId(redisService.getTradeNo("TZ"));
        bettingBalanceReport.setType(10);
        //当前已完成流水
        bettingBalanceReport.setBetEffect(userEffectBet.multiply(new BigDecimal(100000000)));
        //当前单笔流水需求
        bettingBalanceReport.setSingleBettingDesire(amount.multiply(new BigDecimal(100000000)));//*倍数*10000
        //当前流水总需求
        bettingBalanceReport.setTotalBettingDesire(afterAdjust.multiply(new BigDecimal(100000000)));
        //之前的剩余流水需求
        bettingBalanceReport.setLeftBettingDesireBefore(requiredBet.multiply(new BigDecimal(100000000)));
        //之后的剩余流水需求
        bettingBalanceReport.setLeftBettingDesireAfter(afterAdjust.multiply(new BigDecimal(100000000)));
        //真实/虚拟账户
        bettingBalanceReport.setOperator(operator);
        bettingBalanceReport.setIsFake(userDO.getIsFake());
        bettingBalanceReport.setRemark(remark);
        bettingBalanceReport.setFinishTime(now);
        bettingBalanceReport.setCoin(coinCode);
        reportService.reportBettingBalance(bettingBalanceReport);
    }

    public void clean(EffectCleanDto cleanDto, GlAdminDO admin) throws GlobalException {
        String coinCode = cleanDto.getCoinCode();
        RPCResponse<GlUserDO> rpcUser = userService.findById(cleanDto.getUserId());
        GlUserDO user = RPCResponseUtils.getData(rpcUser);
        if (user == null) {
            throw new GlobalException("会员不存在");
        }
        RPCResponse<GlUserManageDO> rpcResponse = userManageService.last(cleanDto.getUserId());
        GlUserManageDO last = RPCResponseUtils.getData(rpcResponse);
        if (null != last && (last.getStatus() == 0 || last.getStatus() == 1)) {
            throw new GlobalException("该会员有未审核的操作，请先审核相关记录");
        }
        Date now = new Date();
        GlUserManageDO manage = new GlUserManageDO();
        manage.setCreateTime(now);
        manage.setCreator(admin.getUsername());
        manage.setOptDesc(UserConstant.UserOperateType.CLEAN_FREEZE.getDesc());
        manage.setOptType(UserConstant.UserOperateType.CLEAN_FREEZE.getOptType());
        manage.setStatus(0);
        manage.setUserId(user.getId());
        manage.setUsername(user.getUsername());
        manage.setUserType(user.getUserType());
        manage.setOptData("流水清零");
        manage.setCoin(coinCode);

        //获取剩余流水
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(cleanDto.getUserId(), coinCode);
        manage.setOptBeforeData("需求流水:" + effectBet.getRequiredBet().setScale(2, RoundingMode.DOWN)+coinCode);
        manage.setOptAfterData("需求流水:0"+coinCode);
        manage.setRemark(cleanDto.getRemark());
        userManageService.saveManage(manage);
    }
}
