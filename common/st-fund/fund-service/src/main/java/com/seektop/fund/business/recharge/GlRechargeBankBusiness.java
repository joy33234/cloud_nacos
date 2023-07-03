package com.seektop.fund.business.recharge;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.param.bankcard.RechargeBankListParamDO;
import com.seektop.fund.mapper.GlRechargeBankMapper;
import com.seektop.fund.model.GlRechargeBank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class GlRechargeBankBusiness extends AbstractBusiness<GlRechargeBank> {

    private final GlRechargeBankMapper glRechargeBankMapper;

    public PageInfo<GlRechargeBank> findList(GlAdminDO adminDO, RechargeBankListParamDO paramDO) {
        PageHelper.startPage(paramDO.getPage(), paramDO.getSize());
        Condition condition = new Condition(GlRechargeBank.class);
        Example.Criteria criteria = condition.createCriteria();
        // 通过银行名字查询
        if (StringUtils.hasText(paramDO.getBankName())) {
            criteria.andEqualTo("bankName", paramDO.getBankName());
        }
        // 通过币种查询
        if (StringUtils.hasText(paramDO.getCoin())) {
            criteria.andEqualTo("coin", paramDO.getCoin());
        }
        condition.setOrderByClause(" sort asc");
        return new PageInfo<>(findByCondition(condition));
    }

}