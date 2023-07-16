package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.OrderStatusEnum;
import com.ruoyi.okx.mapper.CoinMapper;
import com.ruoyi.okx.utils.Constant;
import io.swagger.models.auth.In;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    @Lazy
    private BuyRecordBusiness buyRecordBusiness;


    public BigDecimal calculateStandard(OkxCoinTicker ticker) {
        BigDecimal standard = BigDecimal.ZERO;
        try {
            if (ticker.getMonthAverage().compareTo(BigDecimal.ZERO) <= 0) {
                return standard;
            }
            standard = ticker.getLast().add(ticker.getAverage()).add(ticker.getMonthAverage()).divide(new BigDecimal(3), 8, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("计算标准价格异常");
                    e.printStackTrace();
        }
        return standard;
    }



    public OkxCoin findOne(String coin) {
        LambdaQueryWrapper<OkxCoin> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxCoin::getCoin, coin);
        return this.coinMapper.selectOne(wrapper);
    }


    @Transactional(rollbackFor = Exception.class)
    public void syncCoinDb() {
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

//    public List<OkxCoin> getCoinCache() {
//        List<OkxCoin> list = Lists.newArrayList();
//        Collection<String> keys = redisService.keys(CacheConstants.OKX_COIN_KEY + "*");
//        for (String key:keys) {
//            list.add(redisService.getCacheObject(key));
//        }
//        return list;
//    }

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

    public OkxCoin getCoinCache(String coin) {
        OkxCoin okxCoin = redisService.getCacheObject(CacheConstants.OKX_COIN_KEY + coin);
        try {
            int times = 3;
            while (okxCoin == null && times > 0) {
                Thread.sleep(1500);
                okxCoin = redisService.getCacheObject(CacheConstants.OKX_COIN_KEY + coin);
                times--;
            }
        } catch (Exception e) {
            log.error("获取coin缓存异常:{}" , e);
        }
        return okxCoin;
    }



    @Async
    public void updateCache(List<OkxCoin> coins) {
        for (OkxCoin coin : coins) {
            redisService.setCacheObject(CacheConstants.OKX_COIN_KEY + coin.getCoin(), coin);
        }
    }

    public void markBuy(String coin,Integer accountId) {
        try {
            OkxCoin okxCoin = getCoinCache(coin);
            int times = 3;
            while (okxCoin == null && times > 0) {
                Thread.sleep(1500);
                okxCoin = getCoinCache(coin);
                times--;
            }

            String boughtAccountIds = StringUtils.isEmpty(okxCoin.getBoughtAccountIds()) ? accountId.toString() : okxCoin.getBoughtAccountIds() + "," + accountId;
            okxCoin.setBoughtAccountIds(boughtAccountIds);
            redisService.setCacheObject(CacheConstants.OKX_COIN_KEY + coin, okxCoin);
            log.info("markBuy成功更新redis_coin :{} accountId:{}",coin,accountId);
        } catch (Exception e) {
            log.error("tradeCoin 异常 ：{}" ,e.getMessage());
        }
    }


    public void cancelBuy(String coin,Integer accountId) {
        try {
            OkxCoin okxCoin = getCoinCache(coin);
            int times = 3;
            while (okxCoin == null && times > 0) {
                Thread.sleep(1500);
                okxCoin = getCoinCache(coin);
            }
            String boughtAccountIds = okxCoin.getBoughtAccountIds().replace(accountId.toString(),"");
            okxCoin.setBoughtAccountIds(boughtAccountIds);
            redisService.setCacheObject(CacheConstants.OKX_COIN_KEY + coin, okxCoin);
            log.info("cancelBuy成功更新redis_coin :{} accountId:{}",coin,accountId);
        } catch (Exception e) {
            log.error("tradeCoin 异常 ：{}" ,e.getMessage());
        }
    }

    /**
     * 是否已买
     * @param coin
     * @param accountId
     * @return
     */
    public boolean checkBoughtCoin(String coin,Integer accountId,List<OkxBuyRecord> buyRecords, Date now) {
        if (buyRecords.stream().anyMatch(item -> item.getCreateTime().getTime() > DateUtil.getMinTime(now).getTime()
                && (item.getStatus().intValue() == OrderStatusEnum.SUCCESS.getStatus()
                || item.getStatus().intValue() == OrderStatusEnum.PENDING.getStatus()))) {
            return true;
        }

        OkxCoin okxCoin = getCoinCache(coin);
        //get from redis error
        if (ObjectUtils.isEmpty(okxCoin)) {
            return true;
        }
        if (StringUtils.isEmpty(okxCoin.getBoughtAccountIds())) {
            return false;
        }

        return okxCoin.getBoughtAccountIds().contains(accountId.toString());
    }


    public void clearSettingCache()
    {
        Collection<String> keys = redisService.keys(CacheConstants.OKX_COIN_KEY + "*");
        redisService.deleteObject(keys);
    }


    @Transactional(rollbackFor = Exception.class)
    public void initTurnOver() {
        try {
            List<OkxCoin> okxCoins = coinMapper.selectList(new LambdaQueryWrapper());
            for (OkxCoin coin:okxCoins) {
                List<OkxBuyRecord> buyRecords = buyRecordBusiness.findByAccountAndCoin(null, coin.getCoin()).stream()
                        .filter(item -> item.getStatus().intValue() != OrderStatusEnum.CANCEL.getStatus())
                        .filter(item -> item.getStatus().intValue() != OrderStatusEnum.FAIL.getStatus())
                        .collect(Collectors.toList());
                if (CollectionUtils.isEmpty(buyRecords)) {
                    continue;
                }
                Integer finishCount = buyRecords.stream().filter(item -> item.getStatus().intValue() == OrderStatusEnum.FINISH.getStatus()).collect(Collectors.toList()).size();
                coin.setTurnOver(new BigDecimal(finishCount).divide(new BigDecimal(buyRecords.size()), 4, RoundingMode.DOWN));
            }
            saveOrUpdateBatch(okxCoins);
        } catch (Exception e) {
            log.error("initTurnOver error:{}", e.getMessage());
        }
    }


}
