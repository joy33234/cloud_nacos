package com.seektop.fund.business.recharge;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlRechargeReceiveInfoMapper;
import com.seektop.fund.model.GlRechargeReceiveInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GlRechargeReceiveInfoBusiness extends AbstractBusiness<GlRechargeReceiveInfo> {

    @Autowired
    private GlRechargeReceiveInfoMapper glRechargeReceiveInfoMapper;
}
