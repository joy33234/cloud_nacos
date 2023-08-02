package com.ruoyi.okx.business;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.mapper.CoinProfitMapper;
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import com.ruoyi.okx.params.DO.OkxCoinProfitDo;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CoinProfitBusiness extends ServiceImpl<CoinProfitMapper, OkxCoinProfit> {

    @Resource
    private CoinProfitMapper mapper;

    @Autowired
    private RedisService redisService;

    @Resource
    private AccountBalanceBusiness accountBalanceBusiness;

    @Resource
    private CoinBusiness coinBusiness;

    @Resource
    @Lazy
    private TickerBusiness tickerBusiness;

    public List<OkxCoinProfit> selectList(OkxCoinProfitDo profitDo){
        if (profitDo == null) {
            return list().stream().sorted(Comparator.comparing(OkxCoinProfit::getUpdateTime).reversed()).collect(Collectors.toList());
        }
        LambdaQueryWrapper<OkxCoinProfit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(profitDo.getAccountId() != null ,OkxCoinProfit::getAccountId, profitDo.getAccountId());
        wrapper.eq(profitDo.getCoin() != null ,OkxCoinProfit::getCoin, profitDo.getCoin());
        wrapper.eq(profitDo.getId() != null ,OkxCoinProfit::getId, profitDo.getId());
        wrapper.orderByDesc(OkxCoinProfit::getUpdateTime);
        List<OkxCoinProfit> profits =  mapper.selectList(wrapper);
        profits.add(0,new OkxCoinProfit(0,"USDT",profitDo.getAccountId(), BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO, BigDecimal.ONE));

        List<OkxAccountBalance> balances = accountBalanceBusiness.list(new OkxAccountBalanceDO(null,null,null,profitDo.getAccountId(),null));
        for (OkxCoinProfit profit:profits) {
            balances.stream().filter(item -> item.getCoin().equalsIgnoreCase(profit.getCoin())).findFirst().ifPresent(obj -> {
                profit.setBalance(obj.getBalance());
                OkxCoinTicker coinTicker = tickerBusiness.getTickerCache(profit.getCoin());
                if (ObjectUtils.isNotEmpty(obj.getBalance()) && ObjectUtils.isNotEmpty(coinTicker) && ObjectUtils.isNotEmpty(coinTicker.getLast())) {
                    profit.setBalanceUsdt(obj.getBalance().multiply(coinTicker.getLast()));
                }
            });
        }
        return profits;
    }

    public static void main(String[] args) {
        BigDecimal a = BigDecimal.ZERO;
        BigDecimal b = BigDecimal.ONE;
        BigDecimal c = a.multiply(b);
        BigDecimal d = b.multiply(a);
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
            profit = new OkxCoinProfit(null,sellRecord.getCoin(), sellRecord.getAccountId(),sellRecord.getProfit(),null,BigDecimal.ZERO,coinBusiness.findOne(sellRecord.getCoin()).getUnit());
        } else {
            profit.setProfit(profit.getProfit().add(sellRecord.getProfit()));
        }
        profit.setUpdateTime(new Date());
        profit.setRemark("计算利润");
        saveOrUpdate(profit);
    }
}
