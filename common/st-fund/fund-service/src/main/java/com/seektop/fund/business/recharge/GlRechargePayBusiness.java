package com.seektop.fund.business.recharge;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlRechargePayMapper;
import com.seektop.fund.model.GlRechargePay;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GlRechargePayBusiness extends AbstractBusiness<GlRechargePay> {

    @Autowired
    private GlRechargePayMapper glRechargePayMapper;
}
