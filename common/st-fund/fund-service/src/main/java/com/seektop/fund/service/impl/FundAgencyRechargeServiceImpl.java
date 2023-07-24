package com.seektop.fund.service.impl;

import com.seektop.fund.business.recharge.AgencyRechargeBusiness;
import com.seektop.fund.service.FundAgencyRechargeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.Date;

@Slf4j
@DubboService(timeout = 5000, interfaceClass = FundAgencyRechargeService.class)
public class FundAgencyRechargeServiceImpl implements FundAgencyRechargeService {

    @Resource
    private AgencyRechargeBusiness agencyRechargeBusiness;

    @Override
    public void deleteLogs(Date date) {
        agencyRechargeBusiness.deleteHistoryData(date);
    }
}
