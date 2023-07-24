package com.seektop.fund.business.recharge;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.configuration.CacheConfiguration;
import com.seektop.fund.mapper.GlRechargeSuccessRequestMapper;
import com.seektop.fund.model.GlRechargeSuccessRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GlRechargeSuccessRequestBusiness extends AbstractBusiness<GlRechargeSuccessRequest> {

    @Resource
    private GlRechargeSuccessRequestMapper glRechargeSucreqMapper;

    public Map<String, String> findRechargeRemark(final List<String> orderIds) {
        Condition con = new Condition(GlRechargeSuccessRequest.class);
        Example.Criteria criteria = con.createCriteria();
        criteria.andIn("orderId", orderIds);
        List<GlRechargeSuccessRequest> requests = findByCondition(con);
        return requests.stream().collect(Collectors.toMap(GlRechargeSuccessRequest::getOrderId, GlRechargeSuccessRequest::getRemark));
    }

    @Cacheable(value = CacheConfiguration.FUND_RECHARGE_MANAGE_OPERATORS)
    public List<String> findAllApplicant() {
        return glRechargeSucreqMapper.findAllApplicant();
    }
}
