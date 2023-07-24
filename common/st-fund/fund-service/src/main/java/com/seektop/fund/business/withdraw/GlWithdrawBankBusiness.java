package com.seektop.fund.business.withdraw;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.configuration.CacheConfiguration;
import com.seektop.fund.controller.backend.param.bankcard.WithdrawBankListParamDO;
import com.seektop.fund.mapper.GlWithdrawBankMapper;
import com.seektop.fund.model.GlWithdrawBank;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.List;

@Component
public class GlWithdrawBankBusiness extends AbstractBusiness<GlWithdrawBank> {

    @Resource
    private GlWithdrawBankMapper withdrawBankMapper;

    @Cacheable(value = CacheConfiguration.BANK, unless = "#result == null")
    public Integer getBankIdByName(String bankName) {
        return withdrawBankMapper.getBankIdByName(bankName);
    }

    @Cacheable(value = CacheConfiguration.BANK, unless = "#result == null")
    public GlWithdrawBank findById(Integer bankId){
        return withdrawBankMapper.selectByPrimaryKey(bankId);
    }

    @Cacheable(value = CacheConfiguration.BANK)
    public List<GlWithdrawBank> findAll(){
        return withdrawBankMapper.selectAll();
    }

    public PageInfo<GlWithdrawBank> findList(GlAdminDO adminDO, WithdrawBankListParamDO paramDO) {
        PageHelper.startPage(paramDO.getPage(), paramDO.getSize());
        Condition condition = new Condition(GlWithdrawBank.class);
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

    public List<GlWithdrawBank> findByCoin(String coin) {
        Condition condition = new Condition(GlWithdrawBank.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("coin", coin);
        condition.setOrderByClause(" sort asc");
        return findByCondition(condition);
    }

}