package com.ruoyi.okx.business;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.mapper.CoinMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CoinBusiness extends ServiceImpl<CoinMapper, OkxCoin> {
    private static final Logger log = LoggerFactory.getLogger(CoinBusiness.class);

    @Resource
    private CoinMapper coinMapper;

    @Autowired
    private RedisService redisService;




    public BigDecimal calculateStandard(OkxCoinTicker ticker) {
        BigDecimal standard = BigDecimal.ZERO;
        try {
            standard = ticker.getLast().add(ticker.getAverage()).add(ticker.getMonthAverage()).divide(new BigDecimal(3), 8, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("计算标准价格异常");
                    e.printStackTrace();
        }
        return standard;
    }

    public boolean reduceCount(String coinStr, Integer accountId, BigDecimal count) {
        OkxCoin coin = getCoin(coinStr);
        BigDecimal remainCount = coin.getCount().subtract(count);
        if (remainCount.compareTo(BigDecimal.ZERO) >= 0) {
            coin.setCount(remainCount);
            return this.save(coin);
        }
        log.info("account:{},{}卖出数量:{}异常", new Object[] { accountId, coinStr, count });
        return false;
    }

    public boolean addCount(String coinStr, Integer accountId, BigDecimal count) {
        OkxCoin coin = getCoin(coinStr);
        coin.setCount(coin.getCount().add(count));
        return updateById(coin);
    }

    private OkxCoin findOne(String coin) {
        LambdaQueryWrapper<OkxCoin> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxCoin::getCoin, coin);
        return this.coinMapper.selectOne(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateList(List<OkxCoin> coins) {
        saveOrUpdateBatch(coins);
        return true;
    }


    public List<OkxCoin> selectCoinList(OkxCoin coin){
        LambdaQueryWrapper<OkxCoin> wrapper = new LambdaQueryWrapper();
        if (coin == null) {
            return list();
        }
        if (StringUtils.isNotEmpty(coin.getCoin())) {
            wrapper.eq(OkxCoin::getCoin, coin.getCoin());
        }
        return coinMapper.selectList(wrapper);
    }

    public OkxCoin getCoin(String coin) {
        OkxCoin okxCoin = redisService.getCacheObject(CacheConstants.OKX_COIN_KEY + coin);
        if (okxCoin == null) {
            okxCoin = this.findOne(coin);
            if (okxCoin != null) {
                redisService.setCacheObject(CacheConstants.OKX_COIN_KEY + coin, okxCoin);
            }
        }
        return okxCoin;
    }

    public void resetSettingCache()
    {
        clearSettingCache();
        loadingCache();
    }

    public void loadingCache() {
        List<OkxCoin> coins = list();
        for (OkxCoin coin : coins)
        {
            redisService.setCacheObject(CacheConstants.OKX_COIN_KEY + coin.getCoin(), coin);
        }
    }

    public void clearSettingCache()
    {
        Collection<String> keys = redisService.keys(CacheConstants.OKX_COIN_KEY + "*");
        redisService.deleteObject(keys);
    }

}
