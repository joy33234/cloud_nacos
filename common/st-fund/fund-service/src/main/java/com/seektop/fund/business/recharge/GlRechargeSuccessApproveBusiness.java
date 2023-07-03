package com.seektop.fund.business.recharge;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.configuration.CacheConfiguration;
import com.seektop.fund.mapper.GlRechargeSuccessApproveMapper;
import com.seektop.fund.model.GlRechargeSuccessApprove;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;


/**
 * Created by CodeGenerator on 2018/03/18.
 */
@Component
public class GlRechargeSuccessApproveBusiness extends AbstractBusiness<GlRechargeSuccessApprove> {

    @Resource
    private GlRechargeSuccessApproveMapper glRechargeSucapvMapper;


    @Cacheable(value = CacheConfiguration.FUND_RECHARGE_MANAGE_OPERATORS)
    public List<String> findAllAuditor() {
        return glRechargeSucapvMapper.findAllAuditor();
    }

}
