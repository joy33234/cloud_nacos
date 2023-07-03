package com.seektop.fund.business.monitor;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.controller.backend.param.monitor.RechargePayerMonitorUsernameWhiteListParamDO;
import com.seektop.fund.mapper.RechargePayerMonitorUsernameWhiteListMapper;
import com.seektop.fund.model.RechargePayerMonitorUsernameWhiteList;
import com.seektop.fund.vo.RechargePayerMonitorUsernameWhiteListDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargePayerMonitorUsernameWhiteListBusiness extends AbstractBusiness<RechargePayerMonitorUsernameWhiteList> {

    private final RechargePayerMonitorUsernameWhiteListMapper rechargePayerMonitorUsernameWhiteListMapper;

    public PageInfo<RechargePayerMonitorUsernameWhiteListDO> findList(RechargePayerMonitorUsernameWhiteListParamDO paramDO) {
        PageHelper.startPage(paramDO.getPage(), paramDO.getSize());
        return new PageInfo<>(rechargePayerMonitorUsernameWhiteListMapper.findWhiteList(paramDO.getStartDate(), paramDO.getEndDate(), paramDO.getUsername()));
    }

}