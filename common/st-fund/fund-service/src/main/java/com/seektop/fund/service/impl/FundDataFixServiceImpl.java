package com.seektop.fund.service.impl;

import com.seektop.fund.handler.RechargeHandler;
import com.seektop.fund.handler.WithdrawHandler;
import com.seektop.fund.service.FundDataFixService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@DubboService(timeout = 3000, interfaceClass = FundDataFixService.class)
public class FundDataFixServiceImpl implements FundDataFixService {

    @Resource
    private RechargeHandler rechargeHandler;
    @Resource
    private WithdrawHandler withdrawHandler;

    @Override
    public void rechargeReSynchronize(List<String> orderIdList) {
        rechargeHandler.reSynchronize(orderIdList.stream().toArray(String[]::new));
    }

    @Override
    public void withdrawReSynchronize(List<String> orderIdList) {
        withdrawHandler.reSynchronize(orderIdList.stream().toArray(String[]::new));
    }

    @Override
    public void rechargeReSynchronize(Date startDate, Date endDate) {
        rechargeHandler.synchronize(startDate, endDate);
    }

}