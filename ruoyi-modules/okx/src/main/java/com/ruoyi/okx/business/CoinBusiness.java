package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.CoinStatusEnum;
import com.ruoyi.okx.mapper.CoinMapper;
import com.ruoyi.okx.utils.Constant;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CoinBusiness extends ServiceImpl<CoinMapper, OkxCoin> {
    private static final Logger log = LoggerFactory.getLogger(CoinBusiness.class);

    @Resource
    private CoinMapper coinMapper;




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
        OkxCoin coin = findOne(coinStr);
        BigDecimal remainCount = coin.getCount().subtract(count);
        if (remainCount.compareTo(BigDecimal.ZERO) >= 0) {
            coin.setCount(remainCount);
            return this.save(coin);
        }
        log.info("account:{},{}卖出数量:{}异常", new Object[] { accountId, coinStr, count });
        return false;
    }

    public boolean addCount(String coinStr, Integer accountId, BigDecimal count) {
        OkxCoin coin = findOne(coinStr);
        coin.setCount(coin.getCount().add(count));
        return updateList(Arrays.asList(new OkxCoin[] { coin }));
    }

//    public List<Coin> findByAccountId(Integer accountId) {
//        LambdaQueryWrapper<Coin> wrapper = new LambdaQueryWrapper();
//        wrapper.eq(Coin::getAccountId, accountId);
//        return this.coinMapper.selectList((Wrapper)wrapper);
//    }

    public OkxCoin findOne(String coin) {
        LambdaQueryWrapper<OkxCoin> wrapper = new LambdaQueryWrapper();
//        wrapper.eq(Coin::getAccountId, accountId);
        wrapper.eq(OkxCoin::getCoin, coin);
        return (OkxCoin)this.coinMapper.selectOne((Wrapper)wrapper);
    }

    @Transactional(rollbackFor = {Exception.class})
    public boolean updateList(List<OkxCoin> coins) {
//        coins.stream().forEach(item -> {
 //           coinMapper.updateById(item);
  //      });
        this.saveOrUpdateBatch(coins);
        return true;
    }

//    @Transactional(rollbackFor = {Exception.class})
//    public void updateStatus() {
//        List<OkxCoin> coins = list();
//        Integer riseCount = Integer.valueOf(((List)coins.stream().filter(item -> (item.isRise() == true)).collect(Collectors.toList())).size());
//        boolean isRise = (riseCount.intValue() / (coins.size() - riseCount.intValue()) >= 1);
//        if (isRise == true) {
//            OkxConstants.OKX_TRADE_STATUS = 0;
//        } else {
//            OkxConstants.OKX_TRADE_STATUS = 1;
//        }
//        log.info("riseCount:{}, fallCount:{}, allCount:{},OKX_TRADE_STATUS:{}", new Object[] { riseCount, Integer.valueOf(coins.size() - riseCount.intValue()), Integer.valueOf(coins.size()), Integer.valueOf(OkxConstants.OKX_TRADE_STATUS) });
//    }


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

}
