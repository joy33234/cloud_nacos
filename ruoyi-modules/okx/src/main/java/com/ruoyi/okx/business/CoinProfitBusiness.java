package com.ruoyi.okx.business;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.mapper.CoinProfitMapper;
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import com.ruoyi.okx.params.DO.OkxCoinProfitDo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
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

    public List<OkxCoinProfit> selectList(OkxCoinProfitDo profitDo){
        if (profitDo == null) {
            return list().stream().sorted(Comparator.comparing(OkxCoinProfit::getUpdateTime).reversed()).collect(Collectors.toList());
        }
        LambdaQueryWrapper<OkxCoinProfit> wrapper = new LambdaQueryWrapper();
        wrapper.eq(profitDo.getAccountId() != null ,OkxCoinProfit::getAccountId, profitDo.getAccountId());
        wrapper.eq(profitDo.getCoin() != null ,OkxCoinProfit::getCoin, profitDo.getCoin());
        wrapper.eq(profitDo.getId() != null ,OkxCoinProfit::getId, profitDo.getId());
        wrapper.orderByDesc(OkxCoinProfit::getUpdateTime);
        List<OkxCoinProfit> profits =  mapper.selectList(wrapper);

        List<OkxAccountBalance> balances = accountBalanceBusiness.list(new OkxAccountBalanceDO(null,null,null,profitDo.getAccountId(),null));
        for (OkxCoinProfit profit:profits) {
            balances.stream().filter(item -> item.getCoin().equals(profit.getCoin())).findFirst().ifPresent(obj -> {
                profit.setBalance(obj.getBalance());
            });
        }
        return profits;
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
            profit = new OkxCoinProfit(null,sellRecord.getCoin(), sellRecord.getAccountId(),sellRecord.getProfit(),null,coinBusiness.findOne(sellRecord.getCoin()).getUnit());
        } else {
            profit.setProfit(profit.getProfit().add(sellRecord.getProfit()));
        }
        profit.setUpdateTime(new Date());
        profit.setRemark("计算利润");
        saveOrUpdate(profit);
    }
}
