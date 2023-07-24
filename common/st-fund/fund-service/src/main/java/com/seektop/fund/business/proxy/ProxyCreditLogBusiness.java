package com.seektop.fund.business.proxy;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.ProxyCreditLogMapper;
import com.seektop.fund.model.ProxyCreditLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;


@Slf4j
@Component
public class ProxyCreditLogBusiness extends AbstractBusiness<ProxyCreditLog> {
    @Resource
    private ProxyCreditLogMapper proxyCreditLogMapper;

    public void updateStatus(Integer status, String orderId) {
        proxyCreditLogMapper.updateStatus(status, orderId);
    }


}