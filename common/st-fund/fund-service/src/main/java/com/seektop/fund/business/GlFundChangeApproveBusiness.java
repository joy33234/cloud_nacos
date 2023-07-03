package com.seektop.fund.business;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlFundChangeApproveMapper;
import com.seektop.fund.model.GlFundChangeApprove;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;


@Slf4j
@Component
public class GlFundChangeApproveBusiness extends AbstractBusiness<GlFundChangeApprove> {
    @Resource
    private GlFundChangeApproveMapper glFundChangeApproveMapper;


}