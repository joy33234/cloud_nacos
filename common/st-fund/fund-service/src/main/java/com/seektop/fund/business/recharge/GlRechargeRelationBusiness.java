package com.seektop.fund.business.recharge;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlRechargeRelationMapper;
import com.seektop.fund.model.GlRechargeRelation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GlRechargeRelationBusiness extends AbstractBusiness<GlRechargeRelation> {
    @Autowired
    private GlRechargeRelationMapper glRechargeRelationMapper;
}
