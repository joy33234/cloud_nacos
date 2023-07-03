package com.seektop.fund.business;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlPaymentChannelMapper;
import com.seektop.fund.model.GlPaymentChannel;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.util.List;

@Component
public class GlPaymentChannelBusiness extends AbstractBusiness<GlPaymentChannel> {

    @Resource
    private GlPaymentChannelMapper glPaymentChannelMapper;

    public List<GlPaymentChannel> findValidPaymentChannel() {
        Condition condition = new Condition(GlPaymentChannel.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("status", "0");
        condition.setOrderByClause("sort");
        return glPaymentChannelMapper.selectByCondition(condition);
    }

}
