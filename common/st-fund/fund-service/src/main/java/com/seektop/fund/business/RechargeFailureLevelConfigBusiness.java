package com.seektop.fund.business;

import com.google.common.collect.Maps;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.mapper.RechargeFailureLevelConfigMapper;
import com.seektop.fund.model.RechargeFailureLevelConfig;
import com.seektop.fund.vo.RechargeFailureLevelConfigDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargeFailureLevelConfigBusiness extends AbstractBusiness<RechargeFailureLevelConfig> {

    private final RechargeFailureLevelConfigMapper rechargeFailureLevelConfigMapper;

    public List<RechargeFailureLevelConfigDO> findConfig() {
        return rechargeFailureLevelConfigMapper.findConfig();
    }

    public List<RechargeFailureLevelConfig> getEffectiveConfig() {
        Condition condition = new Condition(RechargeFailureLevelConfig.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("status", 0);
        return findByCondition(condition);
    }

    public Map<Integer, RechargeFailureLevelConfig> getAllConfig() {
        Map<Integer, RechargeFailureLevelConfig> configMap = Maps.newHashMap();
        List<RechargeFailureLevelConfig> configList = getEffectiveConfig();
        for (RechargeFailureLevelConfig rechargeFailureLevelConfig : configList) {
            configMap.put(rechargeFailureLevelConfig.getLevelId(), rechargeFailureLevelConfig);
        }
        return configMap;
    }

    public void confirmRemoveConfig(Integer configFundLevelId, GlAdminDO adminDO) {
        RechargeFailureLevelConfig rechargeFailureLevelConfig = rechargeFailureLevelConfigMapper.selectByPrimaryKey(configFundLevelId);
        if (ObjectUtils.isEmpty(rechargeFailureLevelConfig)) {
            return;
        }
        rechargeFailureLevelConfig.setLevelId(configFundLevelId);
        rechargeFailureLevelConfig.setStatus(1);
        rechargeFailureLevelConfig.setUpdater(adminDO.getUsername());
        rechargeFailureLevelConfig.setUpdateDate(new Date());
        updateByPrimaryKeySelective(rechargeFailureLevelConfig);
    }

    public void confirmUpdateConfig(Integer configFundLevelId, Integer targetFundLevelId, String targetFundLevelName, Integer newUserTimes, Integer oldUserTimes, String vips, GlAdminDO adminDO) {
        RechargeFailureLevelConfig rechargeFailureLevelConfig = rechargeFailureLevelConfigMapper.selectByPrimaryKey(configFundLevelId);
        if (ObjectUtils.isEmpty(rechargeFailureLevelConfig)) {
            rechargeFailureLevelConfig = new RechargeFailureLevelConfig();
            rechargeFailureLevelConfig.setLevelId(configFundLevelId);
            rechargeFailureLevelConfig.setNewUserTimes(newUserTimes);
            rechargeFailureLevelConfig.setOldUserTimes(oldUserTimes);
            rechargeFailureLevelConfig.setTargetLevelId(targetFundLevelId);
            rechargeFailureLevelConfig.setTargetLevelName(targetFundLevelName);
            rechargeFailureLevelConfig.setVips(vips);
            rechargeFailureLevelConfig.setStatus(0);
            rechargeFailureLevelConfig.setUpdater(adminDO.getUsername());
            rechargeFailureLevelConfig.setUpdateDate(new Date());
            save(rechargeFailureLevelConfig);
        } else {
            rechargeFailureLevelConfig.setLevelId(configFundLevelId);
            rechargeFailureLevelConfig.setNewUserTimes(newUserTimes);
            rechargeFailureLevelConfig.setOldUserTimes(oldUserTimes);
            rechargeFailureLevelConfig.setTargetLevelId(targetFundLevelId);
            rechargeFailureLevelConfig.setTargetLevelName(targetFundLevelName);
            rechargeFailureLevelConfig.setUpdater(adminDO.getUsername());
            rechargeFailureLevelConfig.setVips(vips);
            rechargeFailureLevelConfig.setStatus(0);
            rechargeFailureLevelConfig.setUpdateDate(new Date());
            updateByPrimaryKeySelective(rechargeFailureLevelConfig);
        }
    }

}