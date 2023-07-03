package com.seektop.fund.business;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.HandlerResponseCode;
import com.seektop.constant.fund.Constants;
import com.seektop.constant.game.GameChannelConstants;
import com.seektop.constant.user.UserConstant;
import com.seektop.data.param.betting.BettingChannelDto;
import com.seektop.data.param.betting.FindBettingCommParamDO;
import com.seektop.data.param.betting.UserBettingDto;
import com.seektop.data.result.betting.BettingTransferWinInfo;
import com.seektop.data.service.BettingService;
import com.seektop.digital.mapper.DigitalUserAccountMapper;
import com.seektop.digital.model.DigitalUserAccount;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.fund.BettingBalanceEnum;
import com.seektop.enumerate.fund.FundReportEvent;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawConfigBusiness;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawCommonConfig;
import com.seektop.fund.controller.backend.dto.NoticeSuccessDto;
import com.seektop.fund.controller.backend.dto.withdraw.BalanceDto;
import com.seektop.fund.controller.backend.result.GameUserResult;
import com.seektop.fund.dto.param.account.FundUserBalanceChangeVO;
import com.seektop.fund.dto.result.withdraw.GlWithdrawEffectBetDO;
import com.seektop.fund.handler.NoticeHandler;
import com.seektop.fund.handler.UserSyncHandler;
import com.seektop.fund.handler.validation.Validator;
import com.seektop.fund.mapper.GlFundTransferRecordMapper;
import com.seektop.fund.mapper.GlFundUserLevelLockMapper;
import com.seektop.fund.model.*;
import com.seektop.gamebet.dto.param.GameUserSearchParamDO;
import com.seektop.gamebet.dto.result.GameUserDO;
import com.seektop.gamebet.service.GameUserService;
import com.seektop.report.fund.*;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GlFundUserAccountBusiness  {

    @Value("${proxy.user.level.relation:}")
    private String relationMaps;

    @DubboReference(retries = 3, timeout = 5000)
    private GlUserService glUserService;

    @DubboReference(retries = 3, timeout = 5000)
    private BettingService bettingService;

    @DubboReference(retries = 3, timeout = 5000)
    private GameUserService gameUserService;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Resource
    private ReportService reportService;

    @Resource
    private RedisService redisService;

    @Resource(name = "rechargeNoticeHandler")
    private NoticeHandler noticeHandler;

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;

    @Resource
    private GlFundTransferRecordMapper glFundTransferRecordMapper;

    @Resource
    private GlFundUserLevelLockMapper glFundUserLevelLockMapper;

    @Resource
    private GlWithdrawEffectBetBusiness glWithdrawEffectBetBusiness;

    @Resource
    private GlWithdrawConfigBusiness configBusiness;

    @Resource
    private UserSyncHandler userSyncHandler;

    @Resource
    private DynamicKey dynamicKey;

    @Resource
    private DigitalUserAccountMapper digitalUserAccountMapper;


    public Map<Integer, Integer> getRelationMaps() {
        if (StringUtils.isEmpty(relationMaps)) {
            return new HashMap<>();
        }
        Map<Integer, Integer> map = JSON.parseObject(relationMaps, new TypeReference<Map<Integer, Integer>>() {
        });
        return map;
    }

    public BigDecimal getUserAccountBalance(Integer userId) {
        return digitalUserAccountMapper.getBalance(userId, DigitalCoinEnum.CNY.getCode());
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public HandlerResponse doActivityAward(ActivityBonusReport report, GlUserDO userDO) throws GlobalException {
        try {
            HandlerResponse handlerResponse = doBalanceChange(report, userDO);
            return handlerResponse;
        } catch (Exception e) {
            log.error("doActivityAwardWithFreeze error", e);
            throw new GlobalException(e);
        }
    }

    private HandlerResponse doBalanceChange(FundActBalanceReport report, GlUserDO userDO) throws GlobalException {
        try {
            Integer userId = userDO.getId();

            BigDecimal amount = report.getAmount();
            String orderId = report.getTransactionId();
            DigitalCoinEnum coin = DigitalCoinEnum.getDigitalCoin(report.getCoinCode());
            //账变
            Date now = new Date();
            DigitalUserAccount account = getUserAccount(userId,coin);
            BigDecimal amountBefore = account.getBalance();
            BigDecimal amountAfter = null;
            if (account.getBalance() == null) {
                amountAfter = BigDecimal.ZERO.add(amount);
            } else {
                amountAfter = account.getBalance().add(amount);
            }

            FundUserBalanceChangeVO userAccountVO = new FundUserBalanceChangeVO();
            userAccountVO.setTradeId(orderId);
            userAccountVO.setUserId(userId);
            userAccountVO.setAmount(amount);
            userAccountVO.setCoinCode(coin.getCode());
            userAccountVO.setChangeDate(now);
            userAccountVO.setOperator("admin");
            //不需要打流水
            if (report.getFlowMultiple() == null || report.getFlowMultiple() == 0) {
                userAccountVO.setFreezeAmount(BigDecimal.ZERO);
                userAccountVO.setMultiple(0);
                doUserAccountChangeNoEffect(userAccountVO, report.getCoinCode());
            } else {
                userAccountVO.setFreezeAmount(amount.multiply(new BigDecimal(report.getFlowMultiple())));
                userAccountVO.setMultiple(report.getFlowMultiple());
                //兼容历史数据
                if (report.getSubType() == null) {
                    report.setSubType(report.getActId());
                }
                if (report.getType() == null) {
                    report.setType(4);
                }
                doUserAccountChange(userAccountVO, userDO.getIsFake(), report.getType(), report.getSubType(), report.getCoinCode());
            }
            //账变上报
            reportBalanceChange(report, userDO, amountBefore, amountAfter);
            HandlerResponse handlerResponse = HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.SUCCESS.getCode(), null);

            //和活动约定返回账变前后金额
            Map<String, Object> extraInfo = handlerResponse.getExtraInfo();
            extraInfo.put("amountBefore", amountBefore);
            extraInfo.put("amountAfter", amountAfter);
            return handlerResponse;
        } catch (Exception e) {
            log.error("doActivityAwardWithFreeze error", e);
            throw new GlobalException(e);
        }
    }

    /**
     * 增加用户中心钱包余额、不计入流水
     *
     * @return
     */
    public BigDecimal doUserAccountChangeNoEffect(FundUserBalanceChangeVO userAccountVO, String coin) throws GlobalException {
        Integer userId = userAccountVO.getUserId();
        // 用户流水信息
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userId, coin);

        //1.更新用户累计金额
        GlWithdrawEffectBetDO betVO = new GlWithdrawEffectBetDO();
        betVO.setUserId(userAccountVO.getUserId());
        betVO.setIsClean(false);
        betVO.setAmount(userAccountVO.getAmount());
        betVO.setChangeDate(effectBet.getEffectStartTime());
        betVO.setEffectAmount(effectBet.getRequiredBet());
        betVO.setCoin(coin);
        glWithdrawEffectBetBusiness.doWithdrawEffect(betVO);
        //2.中心钱包账变，放在流水更新之后
        BigDecimal afterBalance = addBalance(userId, userAccountVO.getAmount(),coin);
        return afterBalance;
    }

    /**
     * 增加用户余额以及流水计算
     *
     * @param userAccountVO
     * @param isFake
     * @param type
     * @param subType
     * @return 用户中心钱包余额
     * @throws GlobalException
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal doUserAccountChange(FundUserBalanceChangeVO userAccountVO, String isFake, Integer type, Integer subType, String coin) throws GlobalException {
        try {
            Integer userId = userAccountVO.getUserId();

            // 用户流水信息
            GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userId, coin);

            //2. 更新用户提现流水表
            GlWithdrawEffectBetDO betVO = new GlWithdrawEffectBetDO();
            betVO.setUserId(userAccountVO.getUserId());
            betVO.setIsClean(false);
            betVO.setAmount(userAccountVO.getAmount());
            betVO.setChangeDate(effectBet.getEffectStartTime());
            betVO.setEffectAmount(effectBet.getRequiredBet());
            betVO.setCoin(coin);
            //充值前所需流水
            BigDecimal freezeBalanceBefore = effectBet.getRequiredBet();
            log.info("doUserAccountChange=====>tradeId:{},用户流水信息:{}",userAccountVO.getTradeId(),effectBet);
            //1.已完成流水 > 所需流水  重新累计用户提现流水
            BigDecimal freezeBalanceAfter = null;

            // 目前用户当前已完成提现流水
            FindBettingCommParamDO findBettingCommParamDO = new FindBettingCommParamDO();
            findBettingCommParamDO.setUserId(userId);
            findBettingCommParamDO.setStartTime(effectBet.getEffectStartTime() == null ? new Date().getTime(): effectBet.getEffectStartTime().getTime());
            findBettingCommParamDO.setEndTime(userAccountVO.getChangeDate().getTime());
            findBettingCommParamDO.setGamePlatformIds(new ArrayList<>());
            findBettingCommParamDO.setCoinCode(coin);
            RPCResponse<BigDecimal> totalBet = bettingService.sumBettingEffectiveAmount(findBettingCommParamDO);
            if (RPCResponseUtils.isFail(totalBet)) {
                log.error("doUserAccountChange_totalBetRPC_err:{}", totalBet);
                throw new GlobalException(ResultCode.SERVER_ERROR);
            }

            BigDecimal totalBetAmount = totalBet.getData();
            if (totalBetAmount.compareTo(freezeBalanceBefore) >= 0) {
                freezeBalanceAfter = userAccountVO.getFreezeAmount();
                betVO.setIsClean(true);
                betVO.setEffectAmount(userAccountVO.getFreezeAmount());
                betVO.setChangeDate(userAccountVO.getChangeDate());
                log.info("doUserAccountChange=====>tradeId:{},totalBetAmount:{}",userAccountVO.getTradeId(),totalBetAmount);
            } else {
                Boolean lose = effectBet.getLose();
                Optional<Date> startTimeOpt = Optional.ofNullable(effectBet.getLoseStartTime());
                if (effectBet.getUserId().equals(userId) && Boolean.FALSE.equals(lose) && startTimeOpt.isPresent()) {
                    //目标用户:公司盈利金额
                    UserBettingDto queryDto = new UserBettingDto();
                    queryDto.setUserId(userId);
                    queryDto.setStime(startTimeOpt.get().getTime());
                    queryDto.setEtime(userAccountVO.getChangeDate().getTime());
                    queryDto.setCoinCode(coin);
                    //2.输光逻辑判断: 累计帐变金额 - 公司盈利 < 5  例如：累计变动金额：1000、公司盈利998
                    RPCResponse<BigDecimal> totalWin = bettingService.sumWinAmount(queryDto);
                    if (RPCResponseUtils.isFail(totalWin)) {
                        log.error("doUserAccountChange_totalBetRPC_err:{}", totalWin);
                        throw new GlobalException(ResultCode.SERVER_ERROR);
                    }
                    BigDecimal win = totalWin.getData();
                    // 上次提款后累计帐变金额 - 上次提款后公司输赢
                    BigDecimal subAmount = effectBet.getLastTotalBalance().subtract(win);
                    log.info("doUserAccountChange=====>tradeId:{},win:{}",userAccountVO.getTradeId(),win);
                    if (win.compareTo(BigDecimal.valueOf(5)) >= 1 && subAmount.compareTo(BigDecimal.valueOf(5)) < 0) {
                        freezeBalanceAfter = userAccountVO.getFreezeAmount();

                        // 如果输光清零，则重置计算开始时间
                        glWithdrawEffectBetBusiness.resetLose(userId,coin);
                        betVO.setIsClean(true);
                        betVO.setEffectAmount(userAccountVO.getFreezeAmount());
                        betVO.setChangeDate(userAccountVO.getChangeDate());

                        //输光上报
                        BettingBalanceReport report = new BettingBalanceReport();
                        report.setUid(userId);
                        report.setOrderId(redisService.getTradeNo("SG"));
                        report.setType(BettingBalanceEnum.LOSE_CLEAN.getCode());
                        report.setAmount(BigDecimal.ZERO);
                        report.setMagnificationFactor(BigDecimal.ZERO);
                        //当前已完成流水
                        report.setBetEffect(totalBetAmount.multiply(new BigDecimal(100000000)));
                        //当前单笔流水需求
                        report.setSingleBettingDesire(freezeBalanceBefore.negate().multiply(new BigDecimal(100000000)));
                        //当前流水总需求
                        report.setTotalBettingDesire(BigDecimal.ZERO);
                        //之前的剩余流水需求
                        report.setLeftBettingDesireBefore(freezeBalanceBefore.multiply(new BigDecimal(100000000)));
                        //之后的剩余流水需求
                        report.setLeftBettingDesireAfter(BigDecimal.ZERO);
                        //真实/虚拟账户
                        report.setOperator(userAccountVO.getOperator());
                        report.setIsFake(isFake);
                        report.setRemark("系统自动清零");
                        report.setFinishTime(new Date(userAccountVO.getChangeDate().getTime() - 1000));
                        report.setCoin(coin);
                        reportService.bettingBalanceReport(report);

                        totalBetAmount = BigDecimal.ZERO;
                        freezeBalanceBefore = BigDecimal.ZERO;
                        log.info("doUserAccountChange=====>tradeId:{},输光清零,GlWithdrawEffectBetDO:{},BettingBalanceReport:{}",userAccountVO.getTradeId(),betVO,report);
                    }
                    else {
                        // 累加用户所需流水
                        freezeBalanceAfter = effectBet.getRequiredBet().add(userAccountVO.getFreezeAmount());
                        betVO.setChangeDate(effectBet.getEffectStartTime());
                        betVO.setEffectAmount(freezeBalanceAfter);
                        log.info("doUserAccountChange=====>tradeId:{},累加用户所需流水,GlWithdrawEffectBetDO:{},",userAccountVO.getTradeId(),betVO);
                    }
                }
                else {
                    // 累加用户所需流水
                    freezeBalanceAfter = effectBet.getRequiredBet().add(userAccountVO.getFreezeAmount());
                    betVO.setChangeDate(effectBet.getEffectStartTime());
                    betVO.setEffectAmount(freezeBalanceAfter);
                    log.info("doUserAccountChange=====>tradeId:{},累加用户所需流水,GlWithdrawEffectBetDO:{},",userAccountVO.getTradeId(),betVO);
                }
            }
            log.info("doUserAccountChange=====>userId:{},流水before:{},流水after:{},EffectBetDO:{}",userId,freezeBalanceBefore,freezeBalanceAfter,betVO);

            BigDecimal afterBalance = addBalance(userId, userAccountVO.getAmount(), coin);

            //用户提现流水信息变动
            glWithdrawEffectBetBusiness.doWithdrawEffect(betVO);

            //上报流水明细
            BettingBalanceReport report = new BettingBalanceReport();
            report.setUid(userAccountVO.getUserId());
            report.setOrderId(userAccountVO.getTradeId());
            report.setType(type);
            if (null != subType) {
                report.setSubType(subType);
            }
            report.setAmount(userAccountVO.getAmount().multiply(new BigDecimal(100000000)));
            report.setMagnificationFactor(BigDecimal.valueOf(userAccountVO.getMultiple()));
            //当前已完成流水
            report.setBetEffect(totalBetAmount.multiply(new BigDecimal(100000000)));
            //当前单笔流水需求
            report.setSingleBettingDesire(userAccountVO.getFreezeAmount().multiply(new BigDecimal(100000000)));
            //当前流水总需求
            report.setTotalBettingDesire(freezeBalanceAfter.multiply(new BigDecimal(100000000)));
            //之前的剩余流水需求
            report.setLeftBettingDesireBefore(freezeBalanceBefore.multiply(new BigDecimal(100000000)));
            //之后的剩余流水需求
            report.setLeftBettingDesireAfter(freezeBalanceAfter.multiply(new BigDecimal(100000000)));
            //真实/虚拟账户
            report.setOperator(userAccountVO.getOperator());
            report.setIsFake(isFake);
            report.setRemark(userAccountVO.getRemark());
            report.setFinishTime(userAccountVO.getChangeDate());
            report.setCoin(coin);
            reportService.bettingBalanceReport(report);

            return afterBalance;

        } catch (Exception e) {
            log.error("doUserAccountChange_error", e);
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
    }

    public Boolean updateDigitalEffect(Integer userId,String tradeId, String coin,BigDecimal amount,Integer paymentId) throws GlobalException {
        try {
            Date now = new Date();

            GlWithdrawCommonConfig commonConfig = configBusiness.getWithdrawCommonConfig();
            if (null == commonConfig) {
                throw new GlobalException(ResultCode.WITHDRAW_MULTIPLE_ERROR);
            }

            BigDecimal freezeAmount = amount.multiply(BigDecimal.valueOf(commonConfig.getMultiple()));

            // 用户流水信息
            GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userId, coin);

            //2. 更新用户提现流水表
            GlWithdrawEffectBetDO betVO = new GlWithdrawEffectBetDO();
            betVO.setUserId(userId);
            betVO.setIsClean(false);
            betVO.setAmount(amount);
            betVO.setChangeDate(effectBet.getEffectStartTime());
            betVO.setEffectAmount(effectBet.getRequiredBet());
            betVO.setCoin(coin);
            //充值前所需流水
            BigDecimal freezeBalanceBefore = effectBet.getRequiredBet();
            log.info("doUserAccountChange=====>tradeId:{},用户流水信息:{}",tradeId,effectBet);
            //1.已完成流水 > 所需流水  重新累计用户提现流水
            BigDecimal freezeBalanceAfter = null;

            // 目前用户当前已完成提现流水
            FindBettingCommParamDO findBettingCommParamDO = new FindBettingCommParamDO();
            findBettingCommParamDO.setUserId(userId);
            findBettingCommParamDO.setStartTime(effectBet.getEffectStartTime() == null ? new Date().getTime(): effectBet.getEffectStartTime().getTime());
            findBettingCommParamDO.setEndTime(now.getTime());
            findBettingCommParamDO.setGamePlatformIds(new ArrayList<>());
            findBettingCommParamDO.setCoinCode(coin);
            RPCResponse<BigDecimal> totalBet = bettingService.sumBettingEffectiveAmount(findBettingCommParamDO);
            if (RPCResponseUtils.isFail(totalBet)) {
                log.error("doUserAccountChange_totalBetRPC_err:{}", totalBet);
                throw new GlobalException(ResultCode.SERVER_ERROR);
            }

            BigDecimal totalBetAmount = totalBet.getData();
            if (totalBetAmount.compareTo(freezeBalanceBefore) >= 0) {
                freezeBalanceAfter = freezeAmount;
                betVO.setIsClean(true);
                betVO.setEffectAmount(freezeAmount);
                betVO.setChangeDate(now);
                log.info("doUserAccountChange=====>tradeId:{},totalBetAmount:{}",tradeId,totalBetAmount);
            } else {
                Boolean lose = effectBet.getLose();
                Optional<Date> startTimeOpt = Optional.ofNullable(effectBet.getLoseStartTime());
                if (effectBet.getUserId().equals(userId) && Boolean.FALSE.equals(lose) && startTimeOpt.isPresent()) {
                    //目标用户:公司盈利金额
                    UserBettingDto queryDto = new UserBettingDto();
                    queryDto.setUserId(userId);
                    queryDto.setStime(startTimeOpt.get().getTime());
                    queryDto.setEtime(now.getTime());
                    queryDto.setCoinCode(coin);
                    //2.输光逻辑判断: 累计帐变金额 - 公司盈利 < 5  例如：累计变动金额：1000、公司盈利998
                    RPCResponse<BigDecimal> totalWin = bettingService.sumWinAmount(queryDto);
                    if (RPCResponseUtils.isFail(totalWin)) {
                        log.error("doUserAccountChange_totalBetRPC_err:{}", totalWin);
                        throw new GlobalException(ResultCode.SERVER_ERROR);
                    }
                    BigDecimal win = totalWin.getData();
                    // 上次提款后累计帐变金额 - 上次提款后公司输赢
                    BigDecimal subAmount = effectBet.getLastTotalBalance().subtract(win);
                    log.info("doUserAccountChange=====>tradeId:{},win:{}",tradeId,win);
                    if (win.compareTo(BigDecimal.valueOf(5)) >= 1 && subAmount.compareTo(BigDecimal.valueOf(5)) < 0) {
                        freezeBalanceAfter = freezeAmount;

                        // 如果输光清零，则重置计算开始时间
                        glWithdrawEffectBetBusiness.resetLose(userId,coin);
                        betVO.setIsClean(true);
                        betVO.setEffectAmount(freezeAmount);
                        betVO.setChangeDate(now);

                        //输光上报
                        BettingBalanceReport report = new BettingBalanceReport();
                        report.setUid(userId);
                        report.setOrderId(redisService.getTradeNo("SG"));
                        report.setType(BettingBalanceEnum.LOSE_CLEAN.getCode());
                        report.setAmount(BigDecimal.ZERO);
                        report.setMagnificationFactor(BigDecimal.ZERO);
                        //当前已完成流水
                        report.setBetEffect(totalBetAmount.multiply(new BigDecimal(100000000)));
                        //当前单笔流水需求
                        report.setSingleBettingDesire(freezeBalanceBefore.negate().multiply(new BigDecimal(100000000)));
                        //当前流水总需求
                        report.setTotalBettingDesire(BigDecimal.ZERO);
                        //之前的剩余流水需求
                        report.setLeftBettingDesireBefore(freezeBalanceBefore.multiply(new BigDecimal(100000000)));
                        //之后的剩余流水需求
                        report.setLeftBettingDesireAfter(BigDecimal.ZERO);
                        //真实/虚拟账户
                        report.setOperator("admin");
                        report.setIsFake("1");
                        report.setRemark("系统自动清零");
                        report.setFinishTime(new Date(now.getTime() - 1000));
                        report.setCoin(coin);
                        reportService.bettingBalanceReport(report);

                        totalBetAmount = BigDecimal.ZERO;
                        freezeBalanceBefore = BigDecimal.ZERO;
                        log.info("doUserAccountChange=====>tradeId:{},输光清零,GlWithdrawEffectBetDO:{},BettingBalanceReport:{}",tradeId,betVO,report);
                    }
                    else {
                        // 累加用户所需流水
                        freezeBalanceAfter = effectBet.getRequiredBet().add(freezeAmount);
                        betVO.setChangeDate(effectBet.getEffectStartTime());
                        betVO.setEffectAmount(freezeBalanceAfter);
                        log.info("doUserAccountChange=====>tradeId:{},累加用户所需流水,GlWithdrawEffectBetDO:{},",tradeId,betVO);
                    }
                }
                else {
                    // 累加用户所需流水
                    freezeBalanceAfter = effectBet.getRequiredBet().add(freezeAmount);
                    betVO.setChangeDate(effectBet.getEffectStartTime());
                    betVO.setEffectAmount(freezeBalanceAfter);
                    log.info("doUserAccountChange=====>tradeId:{},累加用户所需流水,GlWithdrawEffectBetDO:{},",tradeId,betVO);
                }
            }
            log.info("doUserAccountChange=====>userId:{},流水before:{},流水after:{},EffectBetDO:{}",userId,freezeBalanceBefore,freezeBalanceAfter,betVO);

            //用户提现流水信息变动
            glWithdrawEffectBetBusiness.doWithdrawEffect(betVO);

            //上报流水明细
            BettingBalanceReport report = new BettingBalanceReport();
            report.setUid(userId);
            report.setOrderId(tradeId);

            report.setType(BettingBalanceEnum.RECHARGE.getCode());
            if (null != paymentId) {
                report.setSubType(paymentId);
            }
            report.setAmount(amount.multiply(new BigDecimal(100000000)));
            report.setMagnificationFactor(BigDecimal.valueOf(commonConfig.getMultiple()));
            //当前已完成流水
            report.setBetEffect(totalBetAmount.multiply(new BigDecimal(100000000)));
            //当前单笔流水需求
            report.setSingleBettingDesire(freezeAmount.multiply(new BigDecimal(100000000)));
            //当前流水总需求
            report.setTotalBettingDesire(freezeBalanceAfter.multiply(new BigDecimal(100000000)));
            //之前的剩余流水需求
            report.setLeftBettingDesireBefore(freezeBalanceBefore.multiply(new BigDecimal(100000000)));
            //之后的剩余流水需求
            report.setLeftBettingDesireAfter(freezeBalanceAfter.multiply(new BigDecimal(100000000)));
            //真实/虚拟账户
            report.setOperator("admin");
            report.setIsFake("1");
            report.setFinishTime(now);
            report.setCoin(coin);
            reportService.bettingBalanceReport(report);

        } catch (Exception e) {
            log.error("doUserAccountChange_error", e);
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
        return true;
    }

    public void reportBalanceChange(FundActBalanceReport report, GlUserDO userDO, BigDecimal amountBefore, BigDecimal amountAfter) {
        BalanceChangeReport balanceChangeReport = new BalanceChangeReport();
        balanceChangeReport.setOrderType(report.getEvent());
        balanceChangeReport.setCoin(report.getCoinCode());
        balanceChangeReport.setOrderId(report.getTransactionId());
        balanceChangeReport.setReallyAmount(report.getAmount().multiply(BigDecimal.valueOf(10000)).longValue());
        balanceChangeReport.setUuid(report.getTransactionId());
        balanceChangeReport.setIsFake(userDO.getIsFake());
        balanceChangeReport.setTimestamp(new Date());
        balanceChangeReport.setAmount(report.getAmount().multiply(BigDecimal.valueOf(10000)).longValue());
        balanceChangeReport.setBalanceBefore(amountBefore.multiply(new BigDecimal(10000)).longValue());
        balanceChangeReport.setBalanceAfter(amountAfter.multiply(new BigDecimal(10000)).longValue());
        balanceChangeReport.setUserId(userDO.getId());
        balanceChangeReport.setUserName(userDO.getUsername());
        balanceChangeReport.setParentId(userDO.getParentId());
        balanceChangeReport.setParentName(userDO.getParentName());
        reportService.balanceChangeReport(balanceChangeReport);
    }


    /**
     * 充值成功
     *
     * @param req
     * @param amount
     * @return
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public BigDecimal doRechargeSuccess(GlRecharge req, BigDecimal amount) throws GlobalException {
        try {
            Date now = new Date();
            GlWithdrawCommonConfig commonConfig = configBusiness.getWithdrawCommonConfig();
            if (null == commonConfig) {
                throw new GlobalException(ResultCode.WITHDRAW_MULTIPLE_ERROR);
            }
            //计算用户流水、增加中心钱包余额
            FundUserBalanceChangeVO userAccountVO = new FundUserBalanceChangeVO();
            userAccountVO.setTradeId(req.getOrderId());
            userAccountVO.setUserId(req.getUserId());
            userAccountVO.setAmount(amount);
            userAccountVO.setChangeDate(now);
            userAccountVO.setOperator("admin");
            userAccountVO.setFreezeAmount(amount.multiply(BigDecimal.valueOf(commonConfig.getMultiple())));
            userAccountVO.setMultiple(commonConfig.getMultiple());
            BigDecimal balance = doUserAccountChange(userAccountVO, "1", BettingBalanceEnum.RECHARGE.getCode(), req.getPaymentId(), req.getCoin());

//            digitalUserAccountMapper.balanceRecharge();

            //发送通知
            doRechargePayNotice(req, amount);

            return balance;
        } catch (Exception e) {
            log.error("doRechargeSuccess error", e);
            throw new GlobalException(e.getMessage(), e);
        }
    }

    private void doRechargePayNotice(GlRecharge req, BigDecimal amount) {
        NoticeSuccessDto successDto = new NoticeSuccessDto();
        successDto.setAmount(amount);
        successDto.setOrderId(req.getOrderId());
        successDto.setUserId(req.getUserId());
        successDto.setUserName(req.getUsername());
        successDto.setCoin(DigitalCoinEnum.getDigitalCoin(req.getCoin()).getDesc());
        noticeHandler.doSuccessNotice(successDto);
    }

    public BigDecimal getUserBalance(Integer userId) {
        return getUserBalance(userId,DigitalCoinEnum.CNY);
    }
    public BigDecimal getUserBalance(Integer userId,DigitalCoinEnum coinEnum) {
        DigitalUserAccount account = getUserAccount(userId,coinEnum);
        return account == null ? BigDecimal.ZERO : account.getBalance();
    }

    /**
     * 主账号账变核心
     *
     * @param userId
     * @param amount
     * @return
     */
    @Transactional
    public BigDecimal addBalance(Integer userId, BigDecimal amount,DigitalCoinEnum coinEnum) {
        DigitalUserAccount account = getUserAccount(userId,coinEnum);
        BigDecimal amountAfter = null;
        if (account.getBalance() == null) {
            amountAfter = BigDecimal.ZERO.add(amount);
        } else {
            amountAfter = account.getBalance().add(amount);
        }
        try {
            digitalUserAccountMapper.balanceRecharge(userId,coinEnum.getCode(), amount, new Date());
        } catch (Exception e) {
            //如果消息丢失导致没有用户账号，这里创建账号之后重试一次
            getUserAccount(userId,coinEnum);
            digitalUserAccountMapper.balanceRecharge(userId,coinEnum.getCode(), amount, new Date());
        }
        //异步用户数据
        userSyncHandler.userBalanceSync(userId, coinEnum.getCode(), amountAfter);

        return amountAfter;
    }
    public BigDecimal addBalance(Integer userId, BigDecimal amount, String coin) {
        return addBalance(userId,amount, DigitalCoinEnum.getDigitalCoin(coin));
    }

    /**
     * 1.上次转账成功，直接返回成功结果
     * 2.rpc 调用失败且无账变，不保存记录。返回失败
     * 3.余额不足未发生账变，不保存记录。返回失败
     * 4.转账成功，添加成功的记录。
     * 5.未知异常，状态为3，不能进行重试
     *
     * @param userId
     * @param orderId
     * @param amount     总为正数
     * @param changeType 0 表示加币， 1表示减币
     * @param remark
     * @return
     * @Transactional 用来保证record记录和账变一致
     */
    @Transactional(rollbackFor = Exception.class)
    public HandlerResponse transfer(Integer userId, String orderId, BigDecimal amount, Integer changeType,
                                    String remark, Boolean negative,DigitalCoinEnum coinEnum) throws GlobalException {
        GlFundTransferRecord glFundGameTransferRecord = glFundTransferRecordMapper.selectByPrimaryKey(orderId + "_1");
        try {
            if (glFundGameTransferRecord != null && glFundGameTransferRecord.getStatus() == 1) {
                //ok
                return JSON.parseObject(glFundGameTransferRecord.getResult(), HandlerResponse.class);
            }
            if (glFundGameTransferRecord != null && glFundGameTransferRecord.getStatus() == 3) {
                HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                        HandlerResponseCode.FAIL.getCode(), "系统异常");
                return response;
            }

            RPCResponse<GlUserDO> rpcUserDO = null;
            try {
                rpcUserDO = glUserService.findById(userId);
            } catch (Exception e) {
                log.error("============ orderId = {}, message = {}", orderId, e.getMessage(), e);
                HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                        HandlerResponseCode.FAIL.getCode(), "用户不存在，id=" + userId);
                return response;
            }

            GlUserDO userDO = null;
            if (RPCResponseUtils.isFail(rpcUserDO) || rpcUserDO.getData() == null) {
                // 当正常验证处理
                log.error("查询用户失败  userId = {},message = {}", userId, rpcUserDO.getMessage());
                HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                        HandlerResponseCode.FAIL.getCode(), "用户不存在，id=" + userId);
                return response;
            } else {
                userDO = rpcUserDO.getData();
            }
            BigDecimal amountAfter = BigDecimal.ZERO;
            BigDecimal amountBefore = BigDecimal.ZERO;
            if (changeType == 0) {
                amountAfter = doTransfer(userDO, amount, negative,coinEnum);
                amountBefore = amountAfter.subtract(amount);
            } else {

                try {
                    amountAfter = doTransfer(userDO, amount.negate(), negative,coinEnum);
                } catch (GlobalException e) {
                    HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                            HandlerResponseCode.BALANCE_NOT_ENOUGH.getCode(), "用户余额不足");
                    return response;
                }
                amountBefore = amountAfter.add(amount);
            }
            HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                    HandlerResponseCode.SUCCESS.getCode(), null);

            Map<String, Object> extraInfo = response.getExtraInfo();
            extraInfo.put("amountBefore", amountBefore);
            extraInfo.put("amountAfter", amountAfter);
            extraInfo.put("remark", remark);
            response.setExtraInfo(extraInfo);
            //the first time and success
            saveOrUpdateGlFundTransferRecord(glFundGameTransferRecord, 1, orderId, 1, amount, changeType, userId,
                    JSON.toJSONString(response), remark);
            return response;

        } catch (Exception e) {
            log.error("============ orderId = {}, message = {}", orderId, e.getMessage(), e);
            throw new GlobalException(HandlerResponseCode.FAIL.getCode(), "系统系统，请稍后重试", "null", null);
        }
    }
    public HandlerResponse transfer(Integer userId, String orderId, BigDecimal amount, Integer changeType,
                                    String remark, Boolean negative) throws GlobalException {
        return transfer(userId,orderId,amount,changeType,remark,negative,DigitalCoinEnum.CNY);
    }


    public int saveOrUpdateGlFundTransferRecord(GlFundTransferRecord dbRecord, Integer status, String order,
                                                Integer type, BigDecimal amount, Integer changeType, Integer userId,
                                                String result, String remark) {
        if (dbRecord == null) {
            dbRecord = new GlFundTransferRecord();
            dbRecord.setId(order + "_" + type);
            dbRecord.setOrderId(order);
            dbRecord.setStatus(status);
            dbRecord.setAmount(amount);
            dbRecord.setLastupdate(new Date());
            dbRecord.setResult(result);
            dbRecord.setUserId(userId);
            dbRecord.setType(type);
            dbRecord.setRemark(remark);
            dbRecord.setChangeType(changeType);
            return glFundTransferRecordMapper.insertSelective(dbRecord);

        } else {
            dbRecord.setId(order + "_" + type);
            dbRecord.setOrderId(order);
            dbRecord.setStatus(status);
            dbRecord.setAmount(amount);
            dbRecord.setLastupdate(new Date());
            dbRecord.setResult(result);
            dbRecord.setUserId(userId);
            dbRecord.setRemark(remark);
            dbRecord.setChangeType(changeType);
            int count = glFundTransferRecordMapper.updateByPrimaryKeySelective(dbRecord);
            return count;
        }

    }


    /**
     * 1.上次回滚订单记录不存在，不保存记录。
     * 上次转账记录不存在，返回记录不存在；
     * 上次转账记录存在，尝试账变，成功保存回滚记录，失败保存回滚记录。
     * 2.上次回滚成功，直接返回上次回滚结果
     * 3.上次回滚记录状态为失败，重试转账。
     * 状态为2，上次没有账变，可以进行回滚，并修改状态（例如：rpc 调用异常）
     * 状态为3，未知异常，不进行账变操作，人工解决
     *
     * @param orderId
     * @return 事务说明：
     * 方法看起来 @Transactional 注解似乎没有作用。它保证 glFundGameTransferRecord 和 账变相关
     * 数据库操作保持一致。若账变之后，保存记录失败，glFundGameTransferRecord 可能为3（不会滚）.
     * 或者保存失败（catch（Exception） 中），账变金额回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public HandlerResponse transferRecover(String orderId, Boolean negative,DigitalCoinEnum coinEnum)
        throws GlobalException {
        log.info("transferRecover orderId = {}, negative = {}", orderId, negative);
        Integer userId = null;
        BigDecimal amount = null;
        GlFundTransferRecord glFundGameTransferRecord = glFundTransferRecordMapper.selectByPrimaryKey(orderId + "_-1");
        try {
            if (glFundGameTransferRecord != null && glFundGameTransferRecord.getStatus() == 1) {
                // recover has done
                return JSON.parseObject(glFundGameTransferRecord.getResult(), HandlerResponse.class);
            }
            //last time error  兼容老数据
            if (glFundGameTransferRecord != null && glFundGameTransferRecord.getStatus() == 2) {
                userId = glFundGameTransferRecord.getUserId();
                amount = glFundGameTransferRecord.getAmount();

                RPCResponse<GlUserDO> rpcUserDO = null;
                try {
                    rpcUserDO = glUserService.findById(userId);
                } catch (Exception e) {
                    log.error("============ orderId = {}, message = {}", orderId, e.getMessage(), e);
                    HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                            HandlerResponseCode.FAIL.getCode(), "查询用户失败");
                    return response;
                }
                GlUserDO userDO = null;
                if (RPCResponseUtils.isFail(rpcUserDO) || rpcUserDO.getData() == null) {
                    // 当正常验证处理
                    log.error("查询用户失败  userId = {},message = {}", userId, rpcUserDO.getMessage());
                    HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
                            HandlerResponseCode.FAIL.getCode(), "用户不存在，id=" + userId);
                    return response;
                } else {
                    userDO = rpcUserDO.getData();
                }

                BigDecimal amountAfter = BigDecimal.ZERO;
                BigDecimal amountBefore = BigDecimal.ZERO;
                //加币操作，回滚的时候需要减币
                if (glFundGameTransferRecord.getChangeType() == 0) {
                    try {
                        amountAfter = doTransfer(userDO, amount.negate(), negative,coinEnum);
                    } catch (GlobalException e) {
                        HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                                HandlerResponseCode.BALANCE_NOT_ENOUGH.getCode(), "用户余额不足");
                        return response;
                    }
                    amountBefore = amountAfter.add(amount);
                }
                //减币操作，回滚的时候就是加币
                else {
                    amountAfter = doTransfer(userDO, amount, negative,coinEnum);
                    amountBefore = amountAfter.subtract(amount);
                }
                HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
                        HandlerResponseCode.SUCCESS.getCode(), null);

                Map<String, Object> extraInfo = response.getExtraInfo();
                extraInfo.put("amountBefore", amountBefore);
                extraInfo.put("amountAfter", amountAfter);
                extraInfo.put("remark", glFundGameTransferRecord.getRemark());
                response.setExtraInfo(extraInfo);
                //the first time and success
                saveOrUpdateGlFundTransferRecord(glFundGameTransferRecord, 1, orderId, -1, amount,
                        glFundGameTransferRecord.getChangeType(), userId,
                        JSON.toJSONString(response), glFundGameTransferRecord.getRemark());
                return response;
            }
            //兼容老数据
            if (glFundGameTransferRecord != null && glFundGameTransferRecord.getStatus() == 3) {
                log.error("order = {} had revovered.", orderId);
                HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
                        HandlerResponseCode.FAIL.getCode(), "系统异常");
                return response;
            }

            //did not done
            glFundGameTransferRecord = glFundTransferRecordMapper.selectByPrimaryKey(orderId + "_1");
            if (glFundGameTransferRecord == null) {
                HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
                        HandlerResponseCode.ORDER_NOT_EXIST.getCode(), "没有进行过转入操作，不能进行恢复。 orderId = " + orderId);
                return response;
            }
            if (glFundGameTransferRecord.getStatus() != 1) {
                log.error("orderId = {} 订单状态异常 status = {}", orderId, glFundGameTransferRecord.getStatus());
                HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
                        HandlerResponseCode.FAIL.getCode(), "订单状态异常");
                return response;
            }
            //the first to recover transfer
            userId = glFundGameTransferRecord.getUserId();
            amount = glFundGameTransferRecord.getAmount();
            RPCResponse<GlUserDO> rpcUserDO = null;
            try {
                rpcUserDO = glUserService.findById(userId);
            } catch (Exception e) {
                // 当正常验证处理
                log.error("查询用户失败  userId = {},message = {}", userId, rpcUserDO.getMessage());
                HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
                        HandlerResponseCode.FAIL.getCode(), "查询用户失败");
                return response;
            }

            GlUserDO userDO = null;
            if (RPCResponseUtils.isFail(rpcUserDO) || rpcUserDO.getData() == null) {
                // 当正常验证处理
                log.error("查询用户失败  userId = {},message = {}", userId, rpcUserDO.getMessage());
                HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
                        HandlerResponseCode.FAIL.getCode(), "用户不存在，id=" + userId);
                return response;
            } else {
                userDO = rpcUserDO.getData();
            }

            BigDecimal amountAfter = BigDecimal.ZERO;
            BigDecimal amountBefore = BigDecimal.ZERO;
            //加币操作，回滚的时候需要减币
            if (glFundGameTransferRecord.getChangeType() == 0) {
                try {
                    amountAfter = doTransfer(userDO, amount.negate(), negative,coinEnum);
                } catch (GlobalException e) {
                    HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                            HandlerResponseCode.BALANCE_NOT_ENOUGH.getCode(), "用户余额不足");
                    return response;
                }
                amountBefore = amountAfter.add(amount);
            }
            //减币操作，回滚的时候就是加币
            else {
                amountAfter = doTransfer(userDO, amount, negative,coinEnum);
                amountBefore = amountAfter.subtract(amount);
            }
            HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
                    HandlerResponseCode.SUCCESS.getCode(), null);

            Map<String, Object> extraInfo = response.getExtraInfo();
            extraInfo.put("amountBefore", amountBefore);
            extraInfo.put("amountAfter", amountAfter);
            extraInfo.put("remark", glFundGameTransferRecord.getRemark());
            response.setExtraInfo(extraInfo);
            //the first time and success
            saveOrUpdateGlFundTransferRecord(null, 1, orderId, -1,
                    amount, glFundGameTransferRecord.getChangeType(), userId,
                    JSON.toJSONString(response), glFundGameTransferRecord.getRemark());
            return response;

        } catch (Exception e) {
            log.error("============ orderId = {}, message = {}", orderId, e.getMessage(), e);
            /*HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
                    HandlerResponseCode.FAIL.getCode(), "系统系统，请稍后重试");*/
            throw new GlobalException(HandlerResponseCode.FAIL.getCode(), "系统系统，请稍后重试", "null", null);
        }
    }
    public HandlerResponse transferRecover(String orderId, Boolean negative)throws GlobalException {
        return transferRecover(orderId,negative,DigitalCoinEnum.CNY);
    }

    private BigDecimal doTransfer(GlUserDO userDO, BigDecimal amount, Boolean negative,DigitalCoinEnum coinEnum)
            throws GlobalException {
        Date now = new Date();
        DigitalUserAccount account = getUserAccountForUpdate(userDO.getId(),coinEnum);
        BigDecimal amountAfter = null;
        if (account.getBalance() == null) {
            amountAfter = BigDecimal.ZERO.add(amount);
        } else {
            amountAfter = account.getBalance().add(amount);
        }
        //negative == true,金额可以为负数
        if (negative == null || negative == false) {
            //amount > 0 即为加币，加币之后结果可以为负数
            if (amount.compareTo(BigDecimal.ZERO) == -1) {
                if (amountAfter.compareTo(BigDecimal.ZERO) == -1) {
                    throw new GlobalException("用户余额不足");
                }
            }
        }
        digitalUserAccountMapper.balanceTransferIn(userDO.getId(),coinEnum.getCode() , amount, now);
        //账变上报
        //TODO:数据同步
        return amountAfter;
    }

    @Transactional(rollbackFor = Exception.class)
    public DigitalUserAccount createFundAccount(GlUserDO user, DigitalCoinEnum coin, String creator) {

        GlFundUserLevelLock userLevelLock = glFundUserLevelLockMapper.selectByPrimaryKey(user.getId());
        if (ObjectUtils.isEmpty(userLevelLock)) {
            GlFundUserLevelLock levelLock = new GlFundUserLevelLock();
            levelLock.setRegisterDate(user.getRegisterDate());
            levelLock.setBetTotal(BigDecimal.ZERO);
            levelLock.setRechargeTimes(0);
            levelLock.setRechargeTotal(BigDecimal.ZERO);
            levelLock.setWithdrawTimes(0);
            levelLock.setWithdrawTotal(BigDecimal.ZERO);
            levelLock.setStatDate(user.getRegisterDate());
            levelLock.setUserId(user.getId());
            levelLock.setCreateDate(user.getRegisterDate());
            // 特殊代理层级内的新注册用户，默认绑定到指定会员层级
            levelLock.setLevelId(1);
            if (user.getUserType() != null && user.getUserType() == UserConstant.Type.PROXY) {
                // B体育代理默认层级(代理兜底层，ID是2)
                levelLock.setLevelId(2);
            }
            if (!ObjectUtils.isEmpty(relationMaps) && user.getParentId() != null) {
                GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(user.getParentId());
                Map<Integer, Integer> maps = getRelationMaps();
                if (null != userlevel && maps.containsKey(userlevel.getLevelId().intValue())) {
                    levelLock.setLevelId(maps.get(userlevel.getLevelId()));
                }
            }
            levelLock.setUserName(user.getUsername());
            levelLock.setLastUpdate(user.getRegisterDate());
            levelLock.setLastOperator(creator);
            levelLock.setStatus(0);
            glFundUserLevelLockMapper.insert(levelLock);
        }

        // 检查是否存在用户账户信息
        coin = Optional.ofNullable(coin).orElse(DigitalCoinEnum.CNY);
        DigitalUserAccount param = new DigitalUserAccount();
        param.setUserId(user.getId());
        param.setCoin(coin.getCode());
        DigitalUserAccount userAccount = digitalUserAccountMapper.selectOne(param);

        if (ObjectUtils.isEmpty(userAccount) && !user.getIsFake().equals("1")) {
            userAccount = new DigitalUserAccount();
            userAccount.setUserId(user.getId());
            userAccount.setCoin(coin.getCode());
            userAccount.setBalance(Constants.DIGITAL_DEFAULT_BALANCE);
            userAccount.setFreeze(BigDecimal.ZERO);
            userAccount.setValidBalance(BigDecimal.ZERO);
            userAccount.setCreateDate(new Date());
            digitalUserAccountMapper.insert(userAccount);
        }
        //会员流水详情
        if (user.getUserType() == UserConstant.Type.PLAYER) {
            GlWithdrawEffectBet effectBet = new GlWithdrawEffectBet();
            effectBet.setUserId(user.getId());
            effectBet.setLoseClean(1);
            effectBet.setGrandTotalBalance(BigDecimal.ZERO);
            effectBet.setRequiredBet(BigDecimal.ZERO);
            effectBet.setEffectStartTime(user.getRegisterDate());
            effectBet.setCreateDate(user.getRegisterDate());
            effectBet.setLastUpdate(user.getRegisterDate());
            effectBet.setLose(true); // 创建账号时开始计算
            effectBet.setCoin(coin.getCode());
            glWithdrawEffectBetBusiness.save(effectBet);
        }
        return userAccount;
    }

    public DigitalUserAccount getUserAccount(Integer userId, DigitalCoinEnum coin) {
        DigitalUserAccount param = new DigitalUserAccount();
        param.setUserId(userId);
        param.setCoin(coin.getCode());
        DigitalUserAccount userAccount = digitalUserAccountMapper.selectOne(param);
        // 正常数据
        if (userAccount != null) {
            return userAccount;
        }
        // 调用异常返回null
        RPCResponse<GlUserDO> rpcResponse = glUserService.findById(userId);
        if (RPCResponseUtils.isFail(rpcResponse)) {
            return null;
        }
        // 调用正常，但是没有数据
        if (rpcResponse.getData() == null) {
            return null;
        }
        return createFundAccount(rpcResponse.getData(), coin, "system");
    }

    public DigitalUserAccount getUserAccountForUpdate(Integer userId, DigitalCoinEnum coin) {
        //正常数据
        DigitalUserAccount userAccount = getUserAccount(userId,coin);
        if (userAccount != null) {
            return userAccount;
        }
        //调用异常返回null
        RPCResponse<GlUserDO> rpcResponse = glUserService.findById(userId);
        if (RPCResponseUtils.isFail(rpcResponse)) {
            return null;
        }
        //调用正常，但是没有数据
        if (rpcResponse.getData() == null) {
            return null;
        }
        return createFundAccount(rpcResponse.getData(),coin, "system");
    }

    public List<GameUserResult> getWinAndBalance(BalanceDto balanceDto) throws GlobalException {
        List<GameUserDO> balanceList = getBalance(balanceDto);
        List<GameUserResult> results;
        if (CollectionUtils.isEmpty(balanceList)) {
            results = new ArrayList<>();
        } else {
            List<Integer> channelIds = balanceList.stream().map(GameUserDO::getChannelId).collect(Collectors.toList());
            channelIds = channelIds.stream().distinct().collect(Collectors.toList());
            // 查询数据中心接口
            BettingChannelDto dto = new BettingChannelDto();
            dto.setUserId(balanceDto.getUserId());
            dto.setChannelIds(channelIds);
            dto.setCoinCode(balanceDto.getCoinCode());
            Optional.ofNullable(balanceDto.getStartTime())
                    .ifPresent(time -> dto.setStime(time.getTime()));
            Optional.ofNullable(balanceDto.getEndTime())
                    .ifPresent(time -> dto.setEtime(time.getTime()));
            List<BettingTransferWinInfo> transferList = RPCResponseUtils.getData(bettingService.getUserWinTransferByChannel(dto));
            Map<Integer, BettingTransferWinInfo> map = transferList.stream().collect(Collectors.toMap(a -> a.getChannel(), a -> a));
            DecimalFormat df = new DecimalFormat("0.00");
            results = balanceList.stream().map(b -> {
                GameUserResult r = new GameUserResult();
                BeanUtils.copyProperties(b, r);
                BettingTransferWinInfo info = map.get(r.getChannelId());
                if (info != null) {
                    r.setTransferDiff(info.getTransIn().subtract(info.getTransOut()));
                    r.setValidAmount(info.getValidAmount());
                    BigDecimal win = info.getWin();
                    r.setWin(win);
                    double betCount = info.getBetCount();
                    double validAmount = info.getValidAmount().doubleValue();
                    double winBetCount = info.getWinBetCount();
                    double lowOddsBetCount = info.getLowOddsBetCount();
                    r.setBetCount((int)betCount);
                    r.setLowOddsBetRate(betCount==0?"-":df.format(lowOddsBetCount/betCount*100)+"%");
                    r.setWinBetRate(betCount==0?"-":df.format(winBetCount/betCount*100)+"%");
                    r.setWinRate(validAmount==0?"-":df.format(win.doubleValue()/validAmount*100)+"%");
                }
                return r;
            }).collect(Collectors.toList());
        }
        return results;
    }

    public List<GameUserDO> getBalance(BalanceDto balanceDto) throws GlobalException {
        GlUserDO user = RPCResponseUtils.getData(glUserService.findById(balanceDto.getUserId()));
        Validator.build().add(null == user, "会员不存在").valid();

        GameUserSearchParamDO params = GameUserSearchParamDO.builder().userId(user.getId()).build();
        List<GameUserDO> balanceList = RPCResponseUtils.getData(gameUserService.findGameUser(params));
        if (balanceList == null) {
            balanceList = new ArrayList<>();
        }

        GameUserDO accountBalance = new GameUserDO();
        // 中心钱包
        DigitalUserAccount account = getUserAccount(user.getId(),DigitalCoinEnum.getDigitalCoin(balanceDto.getCoinCode()));
        accountBalance.setBalance(account.getBalance());
        accountBalance.setChannelId(0);
        accountBalance.setCoinCode(account.getCoin());
        accountBalance.setUserId(user.getId());
        accountBalance.setUsername(user.getUsername());
        accountBalance.setValidBalance(account.getValidBalance());
        if (accountBalance.getValidBalance() == null || accountBalance.getValidBalance().compareTo(BigDecimal.ZERO) == -1) {
            accountBalance.setValidBalance(BigDecimal.ZERO);
        }
        balanceList.add(accountBalance);
        // 冻结金额（正在提现的金额）
        BigDecimal withdraw = glWithdrawBusiness.getWithdrawingTotal(user.getId());
        GameUserResult withdrawBalance = new GameUserResult();
        withdrawBalance.setBalance(withdraw);
        withdrawBalance.setChannelId(101);
        withdrawBalance.setUserId(user.getId());
        withdrawBalance.setUsername(user.getUsername());
        withdrawBalance.setValidBalance(BigDecimal.ZERO);
        balanceList.add(withdrawBalance);

        Map<Integer, String> nameMap = new HashMap<>();
        nameMap.put(GameChannelConstants.CHANNEL_AG, "wxg");
        nameMap.put(GameChannelConstants.CHANNEL_EBET, "wxg");
        nameMap.put(GameChannelConstants.CHANNEL_IM_PT, "IM06X_IM06X");
        nameMap.put(GameChannelConstants.CHANNEL_LB, "wlb");
        nameMap.put(GameChannelConstants.CHANNEL_HLQP, "");
        balanceList.forEach(r -> {
            r.setPassword(null);
            String name = nameMap.get(r.getChannelId());
            if (StringUtils.isNotBlank(name)) {
                String username = r.getChannelId() == GameChannelConstants.CHANNEL_HLQP ?
                        r.getUsername().toLowerCase() : r.getUsername();
                r.setUsername(String.format("%s%s", name, username));
            }
        });
        return balanceList;
    }
}