package com.ruoyi.okx.business;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.mapper.OkxProfitMapper;
import com.ruoyi.okx.params.DO.OkxCoinProfitDo;
import com.ruoyi.okx.params.dto.AccountProfitDto;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

@Component
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

        if (ObjectUtil.isEmpty(accountId)) {
            return profitDto;
        } else {
            OkxAccount account = accountBusiness.findOne(accountId);
            profitDto.setAccountId(accountId);
            profitDto.setAccountName(account.getName());
        }

        List<OkxCoinProfit> profits = coinProfitBusiness.selectList(new OkxCoinProfitDo(accountId,null,null));

        BigDecimal finishProfit  = profits.stream().map(OkxCoinProfit::getProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
        profitDto.setFinishProfit(finishProfit);

        BigDecimal unfinishProfit = BigDecimal.ZERO;
        List<OkxBuyRecord> buyRecords = buyRecordBusiness.findUnfinish(accountId,10000);
        List<OkxCoinTicker> tickers = tickerBusiness.findTodayTicker();
        buyRecords.stream().forEach(item -> {
            tickers.stream().filter(obj -> obj.getCoin().equals(item.getCoin())).findFirst().ifPresent(ticker -> {
                unfinishProfit.add(ticker.getLast().subtract(item.getPrice()).multiply(item.getQuantity()));
            });
        });
        profitDto.setUnFinishProfit(unfinishProfit);
        profitDto.setProfit(finishProfit.add(unfinishProfit));
        return profitDto;
    }


}
