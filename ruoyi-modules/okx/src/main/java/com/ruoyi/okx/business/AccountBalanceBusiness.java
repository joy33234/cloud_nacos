package com.ruoyi.okx.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.okx.domain.OkxAccount;
import com.ruoyi.okx.domain.OkxAccountBalance;
import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.domain.OkxCoin;
import com.ruoyi.okx.mapper.OkxAccountBalanceMapper;
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class AccountBalanceBusiness extends ServiceImpl<OkxAccountBalanceMapper, OkxAccountBalance> {

    @Resource
    private OkxAccountBalanceMapper accountBalanceMapper;


    public List<OkxAccountBalance> list(OkxAccountBalanceDO accountBalanceDO) {
        LambdaQueryWrapper<OkxAccountBalance> wrapper = new LambdaQueryWrapper();
        wrapper.eq((null != accountBalanceDO.getId()), OkxAccountBalance::getId, accountBalanceDO.getId());
        wrapper.eq((null != accountBalanceDO.getAccountId()), OkxAccountBalance::getAccountId, accountBalanceDO.getAccountId());
        wrapper.eq((null != accountBalanceDO.getAccountName()), OkxAccountBalance::getAccountName, accountBalanceDO.getAccountName());
        wrapper.eq((null != accountBalanceDO.getCoin()), OkxAccountBalance::getCoin, accountBalanceDO.getCoin());
        wrapper.eq((null != accountBalanceDO.getBalance()), OkxAccountBalance::getBalance, accountBalanceDO.getBalance());
        return this.list(wrapper);
    }

    public boolean save(OkxAccountBalance account) {
        return accountBalanceMapper.insert(account) > 0 ? true : false;
    }

    public boolean update(OkxAccountBalance account) {
        return accountBalanceMapper.updateById(account) > 0 ? true : false;
    }

    public boolean delete(OkxAccountBalance account) {
        return accountBalanceMapper.deleteById(account) > 0 ? true : false;
    }

    public boolean reduceCount(String coinStr, Integer accountId, BigDecimal count) {
        OkxAccountBalance balance = getAccountBalance(coinStr, accountId);
        BigDecimal remainCount = balance.getBalance().subtract(count);
        if (remainCount.compareTo(BigDecimal.ZERO) >= 0) {
            balance.setBalance(remainCount);
            return updateById(balance);
        }
        log.info("account:{},{}卖出数量:{}异常", new Object[] { accountId, coinStr, count });
        return false;
    }

    public boolean addCount(String coinStr, Integer accountId, BigDecimal count) {
        OkxAccountBalance balance = getAccountBalance(coinStr, accountId);
        balance.setBalance(balance.getBalance().add(count));
        return updateById(balance);
    }

    public OkxAccountBalance getAccountBalance (String coin, Integer accountId) {
        LambdaQueryWrapper<OkxAccountBalance> wrapper = new LambdaQueryWrapper();
        wrapper.eq((null != accountId), OkxAccountBalance::getAccountId, accountId);
        wrapper.eq((StringUtils.isNotEmpty(coin)), OkxAccountBalance::getCoin, coin);
        return accountBalanceMapper.selectOne(wrapper);
    }

}
