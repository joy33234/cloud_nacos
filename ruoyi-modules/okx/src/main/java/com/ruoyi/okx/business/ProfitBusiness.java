package com.ruoyi.okx.business;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.mapper.OkxProfitMapper;
import com.ruoyi.okx.params.DO.OkxCoinProfitDo;
import com.ruoyi.okx.params.dto.AccountProfitDto;
import com.ruoyi.okx.utils.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class ProfitBusiness extends ServiceImpl<OkxProfitMapper, OkxCoinProfit> {

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private TickerBusiness tickerBusiness;

    @Resource
    private CoinProfitBusiness coinProfitBusiness;

    @Resource
    private AccountBusiness accountBusiness;


    public AccountProfitDto profit(Integer accountId) {
        AccountProfitDto profitDto = new AccountProfitDto();
        try {
            if (ObjectUtil.isEmpty(accountId)) {
                return profitDto;
            } else {
                OkxAccount account = accountBusiness.findOne(accountId);
                profitDto.setAccountId(accountId);
                profitDto.setAccountName(account.getName());
            }

            //finish_porfit
            CompletableFuture<List<OkxCoinProfit>> finishCf1 = CompletableFuture.supplyAsync(() -> {
                return coinProfitBusiness.selectList(new OkxCoinProfitDo(accountId,null,null));
            });

            //unfinish_profit
            CompletableFuture<List<OkxBuyRecord>> cf1 = CompletableFuture.supplyAsync(() -> {
                return buyRecordBusiness.findUnfinish(accountId,10000);
            });
            CompletableFuture<List<OkxCoinTicker>> cf2 = CompletableFuture.supplyAsync(() -> {
                return tickerBusiness.findTodayTicker();
            });
            final BigDecimal[] unFinishPorfit = {BigDecimal.ZERO};
            CompletableFuture<BigDecimal> cf3 = cf1.thenCombine(cf2, (a, b)  -> {
                a.stream().forEach(item -> {
                    b.stream().filter(obj -> obj.getCoin().equals(item.getCoin())).findFirst().ifPresent(ticker -> {
                        unFinishPorfit[0] = unFinishPorfit[0].add(ticker.getLast().subtract(item.getPrice()).multiply(item.getQuantity()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
                    });
                });
                return unFinishPorfit[0];
            });

            profitDto.setFinishProfit(finishCf1.get().stream().map(OkxCoinProfit::getProfit).reduce(BigDecimal.ZERO, BigDecimal::add));
            profitDto.setUnFinishProfit(cf3.get());
            profitDto.setProfit(profitDto.getFinishProfit().add(cf3.get()));
        } catch (Exception e) {
            log.error("profit error" ,e);
        }
        return profitDto;
    }

}
