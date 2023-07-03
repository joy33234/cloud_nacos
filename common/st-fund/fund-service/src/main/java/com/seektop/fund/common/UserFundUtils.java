package com.seektop.fund.common;

import com.seektop.common.redis.RedisService;
import com.seektop.constant.DateConstant;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
import com.seektop.fund.model.GlFundUserlevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;

@Slf4j
@Component
public class UserFundUtils {

    @Resource
    private RedisService redisService;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Async
    public void setCache(final Integer userId, final Integer levelId, final String levelName, final Integer levelType) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }
        FundUserLevelDO levelCache = redisService.get(KeyConstant.USER.USER_FUND_LEVEL_CACHE + userId, FundUserLevelDO.class);
        if (ObjectUtils.isEmpty(levelCache)) {
            levelCache = new FundUserLevelDO();
        }
        levelCache.setLevelId(levelId);
        levelCache.setLevelName(levelName);
        levelCache.setLevelType(levelType);
        redisService.set(KeyConstant.USER.USER_FUND_LEVEL_CACHE + userId, levelCache, DateConstant.SECOND.DAY);
    }

    public FundUserLevelDO getFundUserLevel(Integer userId) {
        FundUserLevelDO fundUserLevel = redisService.get(KeyConstant.USER.USER_FUND_LEVEL_CACHE + userId, FundUserLevelDO.class);
        if (fundUserLevel != null) {
            return fundUserLevel;
        }
        GlFundUserlevel userLevel = glFundUserlevelBusiness.getUserLevel(userId);
        if (ObjectUtils.isEmpty(userLevel)) {
            return null;
        }
        FundUserLevelDO levelDO = new FundUserLevelDO();
        levelDO.setLevelId(userLevel.getLevelId());
        levelDO.setLevelName(userLevel.getName());
        levelDO.setLevelType(userLevel.getLevelType());
        redisService.set(KeyConstant.USER.USER_FUND_LEVEL_CACHE + userId, levelDO, DateConstant.SECOND.DAY);
        return levelDO;
    }

}