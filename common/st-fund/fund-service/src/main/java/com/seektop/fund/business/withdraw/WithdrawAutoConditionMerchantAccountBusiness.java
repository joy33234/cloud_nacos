package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.configuration.CacheConfiguration;
import com.seektop.fund.mapper.WithdrawAutoConditionMerchantAccountMapper;
import com.seektop.fund.model.GlWithdrawAutoCondition;
import com.seektop.fund.model.WithdrawAutoConditionMerchantAccount;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class WithdrawAutoConditionMerchantAccountBusiness extends AbstractBusiness<WithdrawAutoConditionMerchantAccount> {

    @Autowired
    private WithdrawAutoConditionMerchantAccountMapper conditionMerchantAccountMapper;

    @CacheEvict(value = CacheConfiguration.AUTO_CONDITION_MERCHANT_ACCOUNT, key = "#conditionId")
    public void save(Integer conditionId, List<WithdrawAutoConditionMerchantAccount> merchantAccounts) {
        List<WithdrawAutoConditionMerchantAccount> list = merchantAccounts.stream()
                .filter(a -> !ObjectUtils.isEmpty(a.getMerchantId()))
                .collect(Collectors.toList());
        list.forEach(a -> {
            a.setConditionId(conditionId);
            if (ObjectUtils.isEmpty(a.getLimitAmount())) {
                a.setLimitAmount(BigDecimal.ZERO);
            }
        });
        List<WithdrawAutoConditionMerchantAccount> accounts = findByConditionId(conditionId);
        if (CollectionUtils.isEmpty(accounts)) {
            save(list);
        }
        else {
            Map<Integer, WithdrawAutoConditionMerchantAccount> accountMap = list.stream()
                    .collect(Collectors.toMap(WithdrawAutoConditionMerchantAccount::getMerchantId, a -> a));
            accounts.stream().filter(a -> accountMap.containsKey(a.getMerchantId()))
                    .forEach(a -> {
                        WithdrawAutoConditionMerchantAccount form = accountMap.get(a.getMerchantId());
                        BeanUtils.copyProperties(form, a, "id");
                        updateByPrimaryKeySelective(a);
                    });
            List<WithdrawAutoConditionMerchantAccount> insertList = list.stream()
                    .filter(ma -> accounts.stream().noneMatch(a -> a.getMerchantId().equals(ma.getMerchantId())))
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(insertList)) {
                save(insertList);
            }
            String ids = accounts.stream().filter(a -> !accountMap.containsKey(a.getMerchantId()))
                    .map(a -> String.valueOf(a.getId()))
                    .collect(Collectors.joining(","));
            if (StringUtils.isNotBlank(ids)) {
                deleteByIds(ids);
            }
        }
    }

    public List<WithdrawAutoConditionMerchantAccount> findByConditionId(Integer conditionId){
        WithdrawAutoConditionMerchantAccount account = new WithdrawAutoConditionMerchantAccount();
        account.setConditionId(conditionId);
        return conditionMerchantAccountMapper.queryAll(account);
    }

    public List<WithdrawAutoConditionMerchantAccount> findByConditionIds(List<Integer> conditionIds){
        if (CollectionUtils.isEmpty(conditionIds))
            return new ArrayList<>();
        Condition con = new Condition(WithdrawAutoConditionMerchantAccount.class);
        con.createCriteria().andIn("conditionId", conditionIds);
        return findByCondition(con);
    }

    @Cacheable(value = CacheConfiguration.AUTO_CONDITION_MERCHANT_ACCOUNT, key = "#condition.id")
    public List<WithdrawAutoConditionMerchantAccount> findByCondition(GlWithdrawAutoCondition condition){
        List<WithdrawAutoConditionMerchantAccount> accounts = findByConditionId(condition.getId());
        List<Integer> merchantIds = Arrays.stream(condition.getMerchantId().split(","))
                .map(Integer::parseInt).collect(Collectors.toList());
        return accounts.stream()
                .filter(a -> merchantIds.stream().anyMatch(id -> id.equals(a.getMerchantId())))
                .collect(Collectors.toList());
    }
}
