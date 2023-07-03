package com.seektop.fund.business;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.user.UserConstant;
import com.seektop.data.param.betting.FindBettingCommParamDO;
import com.seektop.data.service.BettingService;
import com.seektop.digital.model.DigitalUserAccount;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawConfigBusiness;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawCommonConfig;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawAmountStatusResult;
import com.seektop.fund.dto.result.withdraw.GlWithdrawEffectBetDO;
import com.seektop.fund.handler.UserSyncHandler;
import com.seektop.fund.mapper.GlWithdrawEffectBetMapper;
import com.seektop.fund.model.GlWithdrawEffectBet;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GlWithdrawEffectBetBusiness extends AbstractBusiness<GlWithdrawEffectBet> {

    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;

    @Reference(retries = 2, timeout = 3000)
    private BettingService bettingService;

    @Resource
    private GlWithdrawEffectBetMapper glWithdrawEffectBetMapper;

    @Resource
    private GlWithdrawConfigBusiness glWithdrawConfigBusiness;

    @Resource
    private GlFundUserAccountBusiness fundUserAccountBusiness;
    @Resource
    private UserSyncHandler userSyncHandler;

    @Transactional(rollbackFor = GlobalException.class)
    public void doWithdrawEffect(GlWithdrawEffectBetDO betVO) throws GlobalException {//TODO joy
        try {
            Date now = new Date();
            GlWithdrawEffectBet effectBet = glWithdrawEffectBetMapper.findOne(betVO.getUserId(),betVO.getCoin());
            //用户提现提水信息
            if (null == effectBet) {
                effectBet = new GlWithdrawEffectBet();
                effectBet.setUserId(betVO.getUserId());
                effectBet.setCoin(betVO.getCoin());
                effectBet.setRequiredBet(betVO.getEffectAmount());
                if (betVO.getIsClean() == true) {
                    effectBet.setLoseClean(1);
                    effectBet.setGrandTotalBalance(betVO.getAmount());
                } else {
                    effectBet.setLoseClean(0);
                    effectBet.setGrandTotalBalance(BigDecimal.ZERO);
                }
                effectBet.setEffectStartTime(betVO.getChangeDate());

                effectBet.setLastTotalBalance(effectBet.getGrandTotalBalance());
                effectBet.setLoseStartTime(effectBet.getEffectStartTime());
                effectBet.setLose(false);

                effectBet.setCreateDate(now);
                effectBet.setLastUpdate(now);
                glWithdrawEffectBetMapper.insert(effectBet);
            } else {
                effectBet.setRequiredBet(betVO.getEffectAmount());
                if (betVO.getIsClean() == true) {
                    effectBet.setLoseClean(1);
                    effectBet.setGrandTotalBalance(betVO.getAmount());
                } else {
                    effectBet.setGrandTotalBalance(effectBet.getGrandTotalBalance().add(betVO.getAmount()));
                }
                effectBet.setEffectStartTime(betVO.getChangeDate());

                if (effectBet.getLose()) {
                    effectBet.setLastTotalBalance(betVO.getAmount());
                    effectBet.setLoseStartTime(betVO.getChangeDate());
                    effectBet.setLose(false);
                }
                else {
                    BigDecimal total = Optional.ofNullable(effectBet.getLastTotalBalance())
                            .orElse(BigDecimal.ZERO).add(betVO.getAmount());
                    effectBet.setLastTotalBalance(total);
                }
                glWithdrawEffectBetMapper.updateByPrimaryKeySelective(effectBet);
            }
        } catch (Exception e) {
            log.error("doWithdrawEffect_err:{}", e);
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
    }

    /**
     * 提现成功后，设置重新计算输光
     * @param userId
     */
    public void resetLose(Integer userId,String coin){//TODO check
        glWithdrawEffectBetMapper.resetLose(userId, coin, true);
    }

    /**
     * 查询用户提现流水已完成信息
     *
     * @param userId
     * @return
     * @throws GlobalException
     */
    public List<GlWithdrawAmountStatusResult> queryWithdrawEffectInfo(Integer userId,String coinCode) throws GlobalException {
        RPCResponse<GlUserDO> rpcResponse = glUserService.findById(userId);
        GlUserDO glUser = RPCResponseUtils.getData(rpcResponse);
        if (glUser == null) {
            throw new GlobalException(ResultCode.DATA_ERROR, "请求用户数据失败");
        }
        List<GlWithdrawAmountStatusResult> list = Lists.newArrayList();
        // 提现通用配置
        GlWithdrawCommonConfig commonConfig = glWithdrawConfigBusiness.getWithdrawCommonConfig();

        Map<String, DigitalCoinEnum> coinEnumMap = DigitalCoinEnum.getCoinMap().entrySet().stream()
                .filter(item -> item.getValue().getIsEnable())
                .filter(item -> StringUtils.isEmpty(coinCode) || coinCode.equals(item.getKey()))
                .collect(Collectors.toMap(p  -> p.getKey(), p -> p.getValue()));

        for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
            // 用户中心钱包信息
            DigitalUserAccount account = fundUserAccountBusiness.getUserAccount(userId, entry.getValue());

            GlWithdrawAmountStatusResult result = new GlWithdrawAmountStatusResult();
            result.setUserId(account.getUserId());
            result.setBalance(account.getBalance().longValue() < 0 ? BigDecimal.ZERO : account.getBalance());
            result.setCoin(entry.getKey());
            // 用户提现流水信息
            GlWithdrawEffectBet effectBet = this.findOne(userId, entry.getKey());

            FindBettingCommParamDO paramDO = new FindBettingCommParamDO();
            paramDO.setUserId(userId);
            paramDO.setStartTime(effectBet.getEffectStartTime().getTime());
            paramDO.setEndTime(new Date().getTime());
            paramDO.setGamePlatformIds(new ArrayList<>());
            paramDO.setCoinCode(entry.getKey());
            RPCResponse<BigDecimal> validBalance = bettingService.sumBettingEffectiveAmount(paramDO);

            if (RPCResponseUtils.isFail(validBalance)) {
                throw new GlobalException(ResultCode.SERVER_ERROR);
            }
            result.setValidBalance(validBalance.getData().setScale(2, RoundingMode.DOWN));

            result.setFreezeBalance(effectBet.getRequiredBet());
            result.setLimit(BigDecimal.valueOf(commonConfig.getMultiple()));
            result.setLastRecharge(effectBet.getEffectStartTime());
            result.setLoseStartTime(effectBet.getLoseStartTime()); // 输光开始统计的时间
            result.setLeftAmount(BigDecimal.ZERO);

            if (glUser.getUserType() == UserConstant.UserType.PLAYER
                    && effectBet.getRequiredBet() != null
                    && effectBet.getRequiredBet().compareTo(BigDecimal.ZERO) == 1) {
                BigDecimal leftAmount = effectBet.getRequiredBet().subtract(result.getValidBalance());
                if (leftAmount.compareTo(BigDecimal.ZERO) == -1) {
                    leftAmount = BigDecimal.ZERO;
                }
                result.setLeftAmount(leftAmount);
                result.setRequireAmount(effectBet.getRequiredBet());
            }
            list.add(result);
        }
        return list;
    }

    public GlWithdrawEffectBet findOne(Integer userId,String coin) throws GlobalException {
        if (ObjectUtils.isEmpty(userId) || StringUtils.isEmpty(coin)) {
            log.error("查询用户流水报错userId:{},coin:{}",userId,coin);
            throw new GlobalException(ResultCode.DATA_ERROR, "请求用户数据失败");
        }
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetMapper.findOne(userId,coin);
        if (effectBet == null) {
            RPCResponse<GlUserDO> rpcResponse = glUserService.findById(userId);
            GlUserDO glUser = RPCResponseUtils.getData(rpcResponse);
            if (glUser == null) {
                throw new GlobalException(ResultCode.DATA_ERROR, "请求用户数据失败");
            }
            effectBet = new GlWithdrawEffectBet();
            effectBet.setUserId(userId);
            effectBet.setLoseClean(1);
            effectBet.setGrandTotalBalance(BigDecimal.ZERO);
            effectBet.setRequiredBet(BigDecimal.ZERO);
            effectBet.setEffectStartTime(glUser.getRegisterDate());
            effectBet.setCreateDate(glUser.getRegisterDate());
            effectBet.setLastUpdate(glUser.getRegisterDate());
            effectBet.setLose(true); // 创建账号时开始计算
            effectBet.setCoin(coin);
            this.save(effectBet);
        }
        return effectBet;
    }

    public List<GlWithdrawEffectBet> findByUserId(Integer userId) {
        return glWithdrawEffectBetMapper.findByUserId(userId);
    }

    /**
     * 获取流水和有效流水信息
     * @param userId
     * @return
     * @throws GlobalException
     */
    public List<GlWithdrawEffectBetDO> getWithdrawEffectBetDO(Integer userId) throws GlobalException {
        List<GlWithdrawEffectBetDO> list = Lists.newArrayList();
        List<GlWithdrawEffectBet> effectBets = glWithdrawEffectBetMapper.findByUserId(userId);

        for (GlWithdrawEffectBet item:effectBets) {
            list.add(getWithdrawEffectBetDO(userId,item.getCoin()));
        }
        return list;
    }


    public GlWithdrawEffectBetDO getWithdrawEffectBetDO(Integer userId, String coin) throws GlobalException {
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetMapper.findOne(userId,coin);

        FindBettingCommParamDO paramDO = new FindBettingCommParamDO();
        paramDO.setUserId(userId);
        paramDO.setStartTime(effectBet.getEffectStartTime().getTime());
        paramDO.setEndTime(new Date().getTime());
        paramDO.setGamePlatformIds(new ArrayList<>());
        RPCResponse<BigDecimal> validBalance = bettingService.sumBettingEffectiveAmount(paramDO);
        GlWithdrawEffectBetDO effectBetDO = new GlWithdrawEffectBetDO();
        effectBetDO.setUserId(userId);
        effectBetDO.setIsClean(false);
        //这个字段不返回
        //effectBetDO.setAmount(null);
        effectBetDO.setChangeDate(effectBet.getEffectStartTime());
        effectBetDO.setEffectAmount(effectBet.getRequiredBet());
        effectBetDO.setValidBalance(RPCResponseUtils.getData(validBalance));

        return effectBetDO;
    }


    /**
     * 修改流水，且同步流水数据
     * @param withdrawEffctBetDO
     */
    public void syncWithdrawEffect(GlWithdrawEffectBetDO withdrawEffctBetDO) throws GlobalException {
        doWithdrawEffect(withdrawEffctBetDO);
        BigDecimal balance = fundUserAccountBusiness.getUserBalance(withdrawEffctBetDO.getUserId());
        userSyncHandler.userBalanceSync(withdrawEffctBetDO.getUserId(),withdrawEffctBetDO.getCoin(), balance);
    }
}
