package com.seektop.fund.business;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.exception.GlobalException;
import com.seektop.fund.mapper.GlFundChangeRelationMapper;
import com.seektop.fund.model.GlFundChangeRelation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class GlFundChangeRelationBusiness extends AbstractBusiness<GlFundChangeRelation> {

    @Resource
    private GlFundChangeRelationMapper glFundChangeRelationMapper;

    public List<GlFundChangeRelation> findOrder(final List<String> orderIds) throws GlobalException {
        String orders = "'"+ org.apache.commons.lang.StringUtils.join(orderIds,"','") + "'";
        return findByIds(orders);
    }

    public Integer saveGlFundChangeRelation(final String relationRechargeOrderId, final String orderId) throws GlobalException {
        GlFundChangeRelation glFundChangeRelation = new GlFundChangeRelation();
        glFundChangeRelation.setOrderId(orderId);
        glFundChangeRelation.setRelationRechargeOrderId(relationRechargeOrderId);
        glFundChangeRelationMapper.insert(glFundChangeRelation);
        return 1;
    }

}