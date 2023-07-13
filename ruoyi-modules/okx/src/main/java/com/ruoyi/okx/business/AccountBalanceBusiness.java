package com.ruoyi.okx.business;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.okx.domain.OkxAccount;
import com.ruoyi.okx.domain.OkxAccountBalance;
import com.ruoyi.okx.domain.OkxCoin;
import com.ruoyi.okx.mapper.OkxAccountBalanceMapper;
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AccountBalanceBusiness extends ServiceImpl<OkxAccountBalanceMapper, OkxAccountBalance> {

    @Resource
    private OkxAccountBalanceMapper accountBalanceMapper;

    @Resource
    private CoinBusiness coinBusiness;

    @Resource
    @Lazy
    private AccountBusiness accountBusiness;

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

    public List<OkxAccountBalance> getBalance(String coinsStr, Map<String, String> map, List<OkxAccountBalance> subList, Date now) {
        String str = HttpUtil.getOkx("/api/v5/account/balance?ccy=" + coinsStr, null, map);
        JSONObject json = JSONObject.parseObject(str);
        if (json == null || !json.getString("code").equals("0")) {
            log.error("str:{}", str);
            throw new ServiceException(str);
        }
        JSONObject data = json.getJSONArray("data").getJSONObject(0);
        JSONArray detail = data.getJSONArray("details");

        if (CollectionUtils.isNotEmpty(subList)) {
            for (OkxAccountBalance okxAccountBalance:subList) {
                for (int j = 0; j < detail.size(); j++) {
                    JSONObject balance = detail.getJSONObject(j);
                    if (okxAccountBalance.getCoin().equalsIgnoreCase(balance.getString("ccy"))) {
                        okxAccountBalance.setBalance(balance.getBigDecimal("availBal"));
                    }
                    okxAccountBalance.setUpdateTime(now);
                }
            }
        } else {
            for (int j = 0; j < detail.size(); j++) {
                JSONObject balance = detail.getJSONObject(j);
                OkxAccountBalance okxAccountBalance = new OkxAccountBalance(null, Integer.valueOf(map.get("id")), map.get("accountName"), balance.getString("ccy"),balance.getBigDecimal("availBal"));
                okxAccountBalance.setCreateTime(now);
                okxAccountBalance.setUpdateTime(now);
                subList.add(okxAccountBalance);
            }
        }
        log.info("getBalance_subList:{}", JSON.toJSONString(subList));
        return subList;
    }

    public void syncAccountBalance(Map<String, String> map, String coin, Date now) {
        List<OkxAccountBalance> subList = Lists.newArrayList();
        OkxAccountBalance accountBalance =  getAccountBalance(coin, Integer.valueOf(map.get("id")));
        if (ObjectUtils.isNotEmpty(accountBalance)) {
            subList.add(accountBalance);
        }
        List<OkxAccountBalance> balanceList = getBalance(coin, map, subList,now);
        updateById(balanceList.get(0));
    }


    @Async
    @Transactional(rollbackFor = Exception.class)
    public void initBalance(String name) {
        try {

            Thread.sleep(50000);
            log.info("initBalance start");
            Date now = new Date();
            List<OkxCoin> okxCoins  = coinBusiness.selectCoinList(null);
            int pages = getPages(okxCoins.size());

            Map<String, String> map = accountBusiness.getAccountMap(name);
            List<OkxAccountBalance> balanceList = Lists.newArrayList();
            String coins = "";
            //帐户币种数量
            for (int i = 0; i < pages; i++) {
                List<OkxCoin> subCoinList = okxCoins.subList(i * 20, Math.min((i + 1) * 20, okxCoins.size()));
                coins = StringUtils.join(subCoinList.stream().map(OkxCoin::getCoin).collect(Collectors.toList()), ",");

                balanceList.addAll(getBalance(coins, map, null, now));
                Thread.sleep(500);
            }
            log.info("initBalance balanceList_size:{}",balanceList.size());

            saveBatch(balanceList);
        } catch (Exception e) {
            log.error("帐号余额初始化异常 {}",e);
        }
    }


    private int getPages(int sum) {
        int pages = sum / 20;
        if (sum % 20 != 0) {
            pages++;
        }
        return pages;
    }


}
