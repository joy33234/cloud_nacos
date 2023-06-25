package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
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
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import com.ruoyi.okx.utils.Constant;
import jdk.nashorn.internal.ir.annotations.Reference;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
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

    @Resource
    TickerBusiness tickerBusiness;




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

        List<OkxCoinTicker> tickerList = tickerBusiness.findTodayTicker();
        list.stream().forEach(item -> {
            item.setTradeMinAmount(item.getStandard().multiply(item.getUnit()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
            item.setVolUsdt24h(item.getVolUsdt24h().setScale(Constant.OKX_BIG_DECIMAL,RoundingMode.DOWN));
            tickerList.stream().filter(ticker -> ticker.getCoin().equals(item.getCoin())).findFirst().ifPresent(obj -> {
                item.setLast(obj.getLast());
            });
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
