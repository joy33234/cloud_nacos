package com.ruoyi.okx.business;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.mapper.CoinProfitMapper;
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import com.ruoyi.okx.params.DO.OkxCoinProfitDo;
import com.ruoyi.okx.params.dto.AccountProfitDto;
import jdk.nashorn.internal.ir.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Component
public class CoinProfitBusiness extends ServiceImpl<CoinProfitMapper, OkxCoinProfit> {
    private static final Logger log = LoggerFactory.getLogger(CoinProfitBusiness.class);

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
            return list();
        }
        LambdaQueryWrapper<OkxCoinProfit> wrapper = new LambdaQueryWrapper();
        wrapper.eq(profitDo.getAccountId() != null ,OkxCoinProfit::getAccountId, profitDo.getAccountId());
        wrapper.eq(profitDo.getCoin() != null ,OkxCoinProfit::getCoin, profitDo.getCoin());
        wrapper.eq(profitDo.getId() != null ,OkxCoinProfit::getId, profitDo.getId());
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
            profit = new OkxCoinProfit(null,sellRecord.getCoin(), sellRecord.getAccountId(),sellRecord.getProfit(),null,coinBusiness.getCoin(sellRecord.getCoin()).getUnit());
        } else {
            profit.setProfit(profit.getProfit().add(sellRecord.getProfit()));
        }
        profit.setUpdateTime(new Date());
        profit.setRemark("计算利润");
        saveOrUpdate(profit);
    }
}
