package com.seektop.fund.business.monitor;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Sets;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.RechargePayerMonitorNameWhiteListMapper;
import com.seektop.fund.model.RechargePayerMonitorNameWhiteList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Condition;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargePayerMonitorNameWhiteListBusiness extends AbstractBusiness<RechargePayerMonitorNameWhiteList> {

    private final RechargePayerMonitorNameWhiteListMapper rechargePayerMonitorNameWhiteListMapper;

    public Set<String> findNameWhiteList() {
        Set<String> nameWhiteSet = Sets.newHashSet();
        List<RechargePayerMonitorNameWhiteList> nameWhiteList = findAll();
        if (CollectionUtils.isEmpty(nameWhiteList)) {
            return nameWhiteSet;
        }
        for (RechargePayerMonitorNameWhiteList nameWhite : nameWhiteList) {
            nameWhiteSet.add(nameWhite.getName());
        }
        return nameWhiteSet;
    }

    public PageInfo<RechargePayerMonitorNameWhiteList> findList(Integer page, Integer size) {
        PageHelper.startPage(page, size);
        Condition condition = new Condition(RechargePayerMonitorNameWhiteList.class);
        condition.setOrderByClause(" create_date desc");
        return new PageInfo<>(findByCondition(condition));
    }

    public Boolean hasExist(String name) {
        return rechargePayerMonitorNameWhiteListMapper.hasExist(name);
    }

}