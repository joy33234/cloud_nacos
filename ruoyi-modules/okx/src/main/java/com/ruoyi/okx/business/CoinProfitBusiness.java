package com.ruoyi.okx.business;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.OkxCoinProfit;
import com.ruoyi.okx.domain.OkxSellRecord;
import com.ruoyi.okx.mapper.CoinProfitMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class CoinProfitBusiness extends ServiceImpl<CoinProfitMapper, OkxCoinProfit> {
    private static final Logger log = LoggerFactory.getLogger(CoinProfitBusiness.class);

    @Resource
    private CoinProfitMapper mapper;

    @Autowired
    private RedisService redisService;


    public List<OkxCoinProfit> selectList(OkxCoinProfit profit){
        LambdaQueryWrapper<OkxCoinProfit> wrapper = new LambdaQueryWrapper();
        if (profit == null) {
            return list();
        }
        wrapper.eq(profit.getAccountId() != null ,OkxCoinProfit::getAccountId, profit.getAccountId());
        wrapper.eq(profit.getCoin() != null ,OkxCoinProfit::getCoin, profit.getCoin());
        wrapper.eq(profit.getId() != null ,OkxCoinProfit::getId, profit.getId());
        return mapper.selectList(wrapper);
    }

    public OkxCoinProfit findOne(Integer accountId, String coin) {
        LambdaQueryWrapper<OkxCoinProfit> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxCoinProfit::getAccountId, accountId);
        wrapper.eq(OkxCoinProfit::getCoin, coin);
        return mapper.selectOne(wrapper);
    }

    public void calculateProfit(OkxSellRecord sellRecord) {
        OkxCoinProfit profit = findOne(sellRecord.getAccountId(),sellRecord.getCoin());
        if (profit == null) {
            profit = new OkxCoinProfit(null,sellRecord.getCoin(), sellRecord.getAccountId(),sellRecord.getProfit());
        } else {
            profit.setProfit(profit.getProfit().add(sellRecord.getProfit()));
        }
        saveOrUpdate(profit);
    }
}
