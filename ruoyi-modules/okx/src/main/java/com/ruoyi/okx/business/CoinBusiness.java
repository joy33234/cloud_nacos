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
import com.ruoyi.okx.utils.Constant;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
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



    private OkxCoin findOne(String coin) {
        LambdaQueryWrapper<OkxCoin> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxCoin::getCoin, coin);
        return this.coinMapper.selectOne(wrapper);
    }


    @Transactional(rollbackFor = Exception.class)
    public void syncCoin() {
        Collection<String> keys = redisService.keys(CacheConstants.OKX_COIN_KEY + "*");
        Date now = new Date();
        for (String key:keys) {
            OkxCoin coin = redisService.getCacheObject(key);
            coin.setUpdateTime(now);
            if (coin != null) {
                coinMapper.updateById(coin);
            }
        }
    }

    public List<OkxCoin> getCoinCache() {
        List<OkxCoin> list = Lists.newArrayList();
        Collection<String> keys = redisService.keys(CacheConstants.OKX_COIN_KEY + "*");
        for (String key:keys) {
            list.add(redisService.getCacheObject(key));
        }
        return list;
    }

    public List<OkxCoin> selectCoinList(OkxCoin coin){
        if (coin == null) {
            return list();
        }
        LambdaQueryWrapper<OkxCoin> wrapper = new LambdaQueryWrapper();
        wrapper.eq(coin.getCoin() != null, OkxCoin::getCoin, coin.getCoin());
        List<OkxCoin> list =  coinMapper.selectList(wrapper);

        list.stream().forEach(item -> {
            item.setTradeMinAmount(item.getStandard().multiply(item.getUnit()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
            item.setVolUsdt24h(item.getVolUsdt24h().setScale(Constant.OKX_BIG_DECIMAL,RoundingMode.DOWN));
        });
        return list;
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


    @Async
    public void updateCache(List<OkxCoin> coins) {
        for (OkxCoin coin : coins) {
            redisService.setCacheObject(CacheConstants.OKX_COIN_KEY + coin.getCoin(), coin);
        }
    }

    public void clearSettingCache()
    {
        Collection<String> keys = redisService.keys(CacheConstants.OKX_COIN_KEY + "*");
        redisService.deleteObject(keys);
    }

}
