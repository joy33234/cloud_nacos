package com.seektop.fund.business;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.mapper.FirstRechargeLevelConfigMapper;
import com.seektop.fund.model.FirstRechargeLevelConfig;
import com.seektop.fund.model.GlFundUserlevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class FirstRechargeLevelConfigBusiness extends AbstractBusiness<FirstRechargeLevelConfig> {

    private final FirstRechargeLevelConfigMapper firstRechargeLevelConfigMapper;

    @Transactional(rollbackFor = GlobalException.class)
    public void confirmCreate(GlAdminDO adminDO, Integer rechargeSuccessTimes, GlFundUserlevel configUserLevel, GlFundUserlevel targetUserLevel) throws GlobalException {
        try {
            FirstRechargeLevelConfig levelConfig = new FirstRechargeLevelConfig();
            levelConfig.setLevelId(configUserLevel.getLevelId());
            levelConfig.setLevelName(configUserLevel.getName());
            levelConfig.setLevelType(configUserLevel.getLevelType());
            levelConfig.setTargetLevelId(targetUserLevel.getLevelId());
            levelConfig.setTargetLevelName(targetUserLevel.getName());
            levelConfig.setRechargeSuccessTimes(rechargeSuccessTimes);
            levelConfig.setStatus((short) 0);
            levelConfig.setCreateDate(new Date());
            levelConfig.setCreator(adminDO.getUsername());
            firstRechargeLevelConfigMapper.insert(levelConfig);
        } catch (Exception ex) {
            throw new GlobalException("保存用户充值成功层级调整配置时发生异常", ex);
        }
    }

    @Transactional(rollbackFor = GlobalException.class)
    public void confirmUpdate(GlAdminDO adminDO, Integer rechargeSuccessTimes, GlFundUserlevel configUserLevel, GlFundUserlevel targetUserLevel) throws GlobalException {
        try {
            FirstRechargeLevelConfig levelConfig = new FirstRechargeLevelConfig();
            levelConfig.setLevelId(configUserLevel.getLevelId());
            levelConfig.setLevelName(configUserLevel.getName());
            levelConfig.setLevelType(configUserLevel.getLevelType());
            levelConfig.setTargetLevelId(targetUserLevel.getLevelId());
            levelConfig.setTargetLevelName(targetUserLevel.getName());
            levelConfig.setRechargeSuccessTimes(rechargeSuccessTimes);
            levelConfig.setUpdateDate(new Date());
            levelConfig.setUpdater(adminDO.getUsername());
            firstRechargeLevelConfigMapper.updateByPrimaryKeySelective(levelConfig);
        } catch (Exception ex) {
            throw new GlobalException("更新用户充值成功层级调整配置时发生异常", ex);
        }
    }

    @Transactional(rollbackFor = GlobalException.class)
    public void confirmUpdate(GlAdminDO adminDO, Integer levelId, Short status) throws GlobalException {
        try {
            FirstRechargeLevelConfig levelConfig = new FirstRechargeLevelConfig();
            levelConfig.setLevelId(levelId);
            levelConfig.setStatus(status);
            levelConfig.setUpdateDate(new Date());
            levelConfig.setUpdater(adminDO.getUsername());
            firstRechargeLevelConfigMapper.updateByPrimaryKeySelective(levelConfig);
        } catch (Exception ex) {
            throw new GlobalException("更新用户充值成功层级调整配置时发生异常", ex);
        }
    }

}