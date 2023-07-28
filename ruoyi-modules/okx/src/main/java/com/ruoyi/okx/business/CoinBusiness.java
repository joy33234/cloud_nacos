package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.exception.GlobalException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.OrderStatusEnum;
import com.ruoyi.okx.mapper.CoinMapper;
import com.ruoyi.okx.params.dto.CoinMark;
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

    @Resource
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
            OkxCoin coin = redisService.getCacheObject(key, OkxCoin.class);
            coin.setUpdateTime(now);
            if (coin != null) {
                coinMapper.updateById(coin);
            }
        }
    }

    public List<OkxCoin> selectCoinList(OkxCoin coin){
        if (coin == null) {
            return list();
        }
        LambdaQueryWrapper<OkxCoin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(coin.getCoin() != null, OkxCoin::getCoin, coin.getCoin());
        List<OkxCoin> list =  coinMapper.selectList(wrapper);

        list.stream().forEach(item -> {
            item.setTradeMinAmount(item.getStandard().multiply(item.getUnit()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
            item.setVolUsdt24h(item.getVolUsdt24h().setScale(Constant.OKX_BIG_DECIMAL,RoundingMode.DOWN));
        });
        return list;
    }

    public OkxCoin getCoinCache(String coin) {
        OkxCoin okxCoin = redisService.getCacheObject(CacheConstants.OKX_COIN_KEY + coin, OkxCoin.class);
        try {
            int times = 3;
            while (okxCoin == null && times > 0) {
                Thread.sleep(1500);
                okxCoin = redisService.getCacheObject(CacheConstants.OKX_COIN_KEY + coin, OkxCoin.class);
                times--;
            }
        } catch (Exception e) {
            log.error("获取coin缓存异常:" , e);
        }
        return okxCoin;
    }



    @Async
    public void updateCache(List<OkxCoin> coins) {
        for (OkxCoin coin : coins) {
            redisService.setCacheObject(CacheConstants.OKX_COIN_KEY + coin.getCoin(), coin);
        }
    }

    public void markBuy(String coin,Integer accountId) throws GlobalException {
        try {
            CoinMark coinMark = getCoinMark(coin);
            coinMark.setAccountIds(coinMark.getAccountIds() + "," + accountId);

            Date now = new Date();
            int second = DateUtil.diffSecond(now, DateUtil.getMaxTime(now));
            redisService.setCacheObject(CacheConstants.OKX_COIN_MARK + coin, coinMark, (long)second, TimeUnit.SECONDS);

            CoinMark afterUpdateCoinMark = getCoinMark(coin);
            int times = 3;
            while (!afterUpdateCoinMark.getAccountIds().contains(accountId.toString()) && times > 0) {
                Thread.sleep(500);
                afterUpdateCoinMark = getCoinMark(coin);
                times--;
            }
            if (afterUpdateCoinMark.getAccountIds().contains(accountId.toString())) {
                log.error("markBuy更新成功coin :{} accountId:{}",coin, accountId);
            } else {
                log.info("markBuy更新失败coin :{} accountId:{}",coin,accountId);
            }
        } catch (Exception e) {
            log.error("markBuy_异常 " ,e);
            throw new GlobalException("markBuy异常");
        }
    }

    public void cancelBuy(String coin,Integer accountId) {
//        try {
//            OkxCoin okxCoin = getCoinCache(coin);
//            int times = 3;
//            while (okxCoin == null && times > 0) {
//                Thread.sleep(1500);
//                okxCoin = getCoinCache(coin);
//            }
//            String boughtAccountIds = okxCoin.getBoughtAccountIds().replace(accountId.toString(),"");
//            okxCoin.setBoughtAccountIds(boughtAccountIds);
//            redisService.setCacheObject(CacheConstants.OKX_COIN_KEY + coin, okxCoin);
//            log.info("cancelBuy成功更新redis_coin :{} accountId:{}",coin,accountId);
//        } catch (Exception e) {
//            log.error("tradeCoin 异常 ：{}" ,e.getMessage());
//        }
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

        CoinMark coinMark = getCoinMark(coin);
        if (ObjectUtils.isEmpty(coinMark) || StringUtils.isEmpty(coinMark.getAccountIds())) {
            return false;
        }
        return coinMark.getAccountIds().contains(accountId.toString());
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
                int finishCount = (int) buyRecords.stream().filter(item -> item.getStatus().intValue() == OrderStatusEnum.FINISH.getStatus()).count();
                BigDecimal turnOver =new BigDecimal(finishCount).divide(new BigDecimal(buyRecords.size()), 4, RoundingMode.DOWN);
                coin.setTurnOver(turnOver);

                OkxCoin cache = getCoinCache(coin.getCoin());
                if (ObjectUtils.isEmpty(cache)) {
                    continue;
                }
                cache.setTurnOver(turnOver);
                updateCache(Collections.singletonList(cache));
            }
            saveOrUpdateBatch(okxCoins);
        } catch (Exception e) {
            log.error("initTurnOver error", e);
        }
    }



    public CoinMark getCoinMark(String coin) {
        CoinMark coinMark = redisService.getCacheObject(CacheConstants.OKX_COIN_MARK + coin, CoinMark.class);
        try {
            int times = 3;
            while (coinMark == null && times > 0) {
                Thread.sleep(200);
                coinMark = redisService.getCacheObject(CacheConstants.OKX_COIN_MARK + coin, CoinMark.class);
                times--;
            }
            if (ObjectUtils.isEmpty(coinMark)) {
                coinMark =  new CoinMark(coin,"");
            }
        } catch (Exception e) {
            log.error("获取coinMark缓存异常" , e);
        }
        return coinMark;
    }



    public void clearCoinMarkCache()
    {
        Collection<String> keys = redisService.keys(CacheConstants.OKX_COIN_MARK + "*");
        redisService.deleteObject(keys);
    }

}
