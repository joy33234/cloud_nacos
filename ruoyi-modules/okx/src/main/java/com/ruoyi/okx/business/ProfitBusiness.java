package com.ruoyi.okx.business;

import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.domain.OkxCoinProfit;
import com.ruoyi.okx.domain.OkxCoinTicker;
import com.ruoyi.okx.params.dto.AccountProfitDto;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

@Component
public class ProfitBusiness {

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private TickerBusiness tickerBusiness;

    @Resource
    private CoinProfitBusiness coinProfitBusiness;


    public AccountProfitDto profit(Integer accountId) {
        AccountProfitDto profitDto = new AccountProfitDto();

        List<OkxCoinProfit> profits = coinProfitBusiness.selectList(new OkxCoinProfit(null,null,accountId,null));
        profitDto.setCoinProfits(profits);

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
