package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlWithdrawReceiveInfoMapper;
import com.seektop.fund.model.GlWithdrawReceiveInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GlWithdrawReceiveInfoBusiness extends AbstractBusiness<GlWithdrawReceiveInfo> {

    @Autowired
    private GlWithdrawReceiveInfoMapper glWithdrawReceiveInfoMapper;
}
