package com.seektop.fund.business.withdraw;

import com.google.common.collect.Lists;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.redis.RedisService;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawPolicyAmountConfig;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawPolicyConfig;
import com.seektop.fund.model.GlWithdrawLevelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.util.List;

@Component
@Slf4j
public class GlWithdrawLevelConfigBusiness extends AbstractBusiness<GlWithdrawLevelConfig> {

    @Resource
    private RedisService redisService;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    public GlWithdrawLevelConfig getWithdrawLevelConfig(Integer levelId,String coinCode) {
        Condition con = new Condition(GlWithdrawLevelConfig.class);
        con.createCriteria().andEqualTo("levelId", levelId)
                .andEqualTo("coin", coinCode)
                .andNotEqualTo("status", 2);
        List<GlWithdrawLevelConfig> list = findByCondition(con);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public List<GlWithdrawLevelConfig> findByLevelId(Integer levelId) {
        Condition con = new Condition(GlWithdrawLevelConfig.class);
        con.createCriteria().andEqualTo("levelId", levelId);
        return findByCondition(con);
    }



    public GlWithdrawPolicyConfig getWithdrawPolicyConfig(Integer userId,String coinCode) throws GlobalException {
        int levelId = glFundUserlevelBusiness.getUserLevelId(userId);
        GlWithdrawLevelConfig levelConfig = getWithdrawLevelConfig(levelId,coinCode);
        if (levelConfig == null || levelConfig.getStatus() != 1) {
            return redisService.getHashObject(RedisKeyHelper.WITHDRAW_POLICY_CONFIG, "POLICY", GlWithdrawPolicyConfig.class);
        }
        GlWithdrawPolicyConfig config = new GlWithdrawPolicyConfig();
        List<GlWithdrawPolicyAmountConfig> list = Lists.newArrayList();
        GlWithdrawPolicyAmountConfig amountConfig = new GlWithdrawPolicyAmountConfig();

        amountConfig.setAmount(levelConfig.getAmount() == null ? 0 : levelConfig.getAmount());
        amountConfig.setDailyAmount(levelConfig.getDailyAmount() == null ? 0 : levelConfig.getDailyAmount());
        amountConfig.setDailyProfit(levelConfig.getDailyProfit() == null ? 0 : levelConfig.getDailyProfit());
        amountConfig.setDailyTimes(levelConfig.getDailyTimes() == null ? 0 : levelConfig.getDailyTimes());
        amountConfig.setFirstAmount(levelConfig.getFirstAmount() == null ? 0 : levelConfig.getFirstAmount());
        amountConfig.setWeeklyAmount(levelConfig.getWeeklyAmount() == null ? 0 : levelConfig.getWeeklyAmount());
        amountConfig.setFirstWithdrawAmount(levelConfig.getFirstWithdrawAmount() == null ? 0 : levelConfig.getFirstWithdrawAmount());
        list.add(amountConfig);
        config.setList(list);

        config.setSameDeviceCheck(levelConfig.getSameDeviceCheck() == null ? 0 : levelConfig.getSameDeviceCheck());
        config.setSameIpCheck(levelConfig.getSameIpCheck() == null ? 0 : levelConfig.getSameIpCheck());
        config.setTime(levelConfig.getTimeCheck() == null ? 0 : levelConfig.getTimeCheck());
        config.setRegisterDays(levelConfig.getRegisterDays() == null ? 0 : levelConfig.getRegisterDays());
        return config;
    }

}