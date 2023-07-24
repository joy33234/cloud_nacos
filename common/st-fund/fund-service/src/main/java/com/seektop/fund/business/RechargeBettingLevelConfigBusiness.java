package com.seektop.fund.business;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.backend.param.recharge.RechargeBettingLevelConfigCreateParamDO;
import com.seektop.fund.controller.backend.param.recharge.RechargeBettingLevelConfigEditParamDO;
import com.seektop.fund.controller.backend.param.recharge.RechargeBettingLevelConfigListParamDO;
import com.seektop.fund.mapper.RechargeBettingLevelConfigMapper;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.RechargeBettingLevelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargeBettingLevelConfigBusiness extends AbstractBusiness<RechargeBettingLevelConfig> {

    private final RechargeBettingLevelConfigMapper rechargeBettingLevelConfigMapper;

    public void confirmDelete(GlAdminDO adminDO, Long recordId) throws GlobalException {
        try {
            RechargeBettingLevelConfig config = new RechargeBettingLevelConfig();
            config.setId(recordId);
            config.setStatus(2);
            config.setUpdater(adminDO.getUsername());
            config.setUpdateDate(new Date());
            updateByPrimaryKeySelective(config);
        } catch (Exception ex) {
            throw new GlobalException("删除充值流水层级调整配置数据时发生异常", ex);
        }
    }

    public void confirmClose(GlAdminDO adminDO, Long recordId) throws GlobalException {
        try {
            RechargeBettingLevelConfig config = new RechargeBettingLevelConfig();
            config.setId(recordId);
            config.setStatus(1);
            config.setUpdater(adminDO.getUsername());
            config.setUpdateDate(new Date());
            updateByPrimaryKeySelective(config);
        } catch (Exception ex) {
            throw new GlobalException("关闭充值流水层级调整配置数据时发生异常", ex);
        }
    }

    public void confirmOpen(GlAdminDO adminDO, Long recordId) throws GlobalException {
        try {
            RechargeBettingLevelConfig config = new RechargeBettingLevelConfig();
            config.setId(recordId);
            config.setStatus(0);
            config.setUpdater(adminDO.getUsername());
            config.setUpdateDate(new Date());
            updateByPrimaryKeySelective(config);
        } catch (Exception ex) {
            throw new GlobalException("开启充值流水层级调整配置数据时发生异常", ex);
        }
    }

    public void confirmEdit(GlAdminDO adminDO, RechargeBettingLevelConfigEditParamDO paramDO, GlFundUserlevel configUserLevel, GlFundUserlevel targetUserLevel) throws GlobalException {
        try {
            RechargeBettingLevelConfig config = new RechargeBettingLevelConfig();
            config.setId(paramDO.getRecordId());
            config.setLevelId(configUserLevel.getLevelId());
            config.setLevelName(configUserLevel.getName());
            config.setDays(paramDO.getDays());
            config.setRechargeAmount(paramDO.getRechargeAmount());
            config.setBettingMultiple(paramDO.getBettingMultiple());
            config.setTargetLevelId(targetUserLevel.getLevelId());
            config.setTargetLevelName(targetUserLevel.getName());
            config.setUpdater(adminDO.getUsername());
            config.setUpdateDate(new Date());
            updateByPrimaryKeySelective(config);
        } catch (Exception ex) {
            throw new GlobalException("编辑充值流水层级调整配置数据时发生异常", ex);
        }
    }

    public void confirmCreate(GlAdminDO adminDO, RechargeBettingLevelConfigCreateParamDO paramDO, GlFundUserlevel configUserLevel, GlFundUserlevel targetUserLevel) throws GlobalException {
        try {
            RechargeBettingLevelConfig config = new RechargeBettingLevelConfig();
            config.setLevelId(configUserLevel.getLevelId());
            config.setLevelName(configUserLevel.getName());
            config.setDays(paramDO.getDays());
            config.setRechargeAmount(paramDO.getRechargeAmount());
            config.setBettingMultiple(paramDO.getBettingMultiple());
            config.setTargetLevelId(targetUserLevel.getLevelId());
            config.setTargetLevelName(targetUserLevel.getName());
            config.setStatus(0);
            config.setUpdater(adminDO.getUsername());
            config.setUpdateDate(new Date());
            save(config);
        } catch (Exception ex) {
            throw new GlobalException("保存充值流水层级调整配置数据时发生异常", ex);
        }
    }

    public PageInfo<RechargeBettingLevelConfig> findRechargeBettingLevelConfig(RechargeBettingLevelConfigListParamDO paramDO) {
        PageHelper.startPage(paramDO.getPage(), paramDO.getSize());
        Condition condition = new Condition(RechargeBettingLevelConfig.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andNotEqualTo("status", 2);
        condition.setOrderByClause(" update_date desc");
        return new PageInfo<>(findByCondition(condition));
    }

    public List<RechargeBettingLevelConfig> findAvailableConfig() {
        Condition condition = new Condition(RechargeBettingLevelConfig.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("status", 0);
        return findByCondition(condition);
    }

}