package com.ruoyi.okx.business;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.CoinStatusEnum;
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.Constant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SyncCoinBusiness {


    @Resource
    private AccountBusiness accountBusiness;


    @Resource
    private CoinBusiness coinBusiness;

    @Resource
    private SettingService settingService;

    @Resource
    private TradeBusiness tradeBusiness;

    @Resource
    private RedisService redisService;

    @Autowired
    private RedisLock redisLock;

    @Resource
    private AccountBalanceBusiness balanceBusiness;

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    public void syncOkxBalance() {
        try {
            List<OkxAccount> accounts = accountBusiness.list();
            for (OkxAccount account:accounts) {
                List<OkxAccountBalance> allBalanceList = balanceBusiness.list(new OkxAccountBalanceDO(null,null,null,account.getId(),null));
                int pages = allBalanceList.size() / 20;
                if (allBalanceList.size() % 20 != 0) {
                    pages++;
                }
                Map<String, String> map = accountBusiness.getAccountMap(account);
                //帐户币种数量
                for (int i = 0; i < pages; i++) {
                    List<OkxAccountBalance> balanceList = allBalanceList.subList(i * 20, ((i + 1) * 20 <= allBalanceList.size()) ? ((i + 1) * 20) : allBalanceList.size());
                    String coins = StringUtils.join(balanceList.stream().map(OkxAccountBalance::getCoin).collect(Collectors.toList()), ",");
                    String str = HttpUtil.getOkx("/api/v5/account/balance?ccy=" + coins, null, map);
                    JSONObject json = JSONObject.parseObject(str);
                    if (json == null || !json.getString("code").equals("0")) {
                        log.error("str:{}", str);
                        return ;
                    }
                    JSONObject data = json.getJSONArray("data").getJSONObject(0);
                    JSONArray detail = data.getJSONArray("details");
                    for (int j = 0; j < detail.size(); j++) {
                        JSONObject balance = detail.getJSONObject(j);
                        allBalanceList.stream().filter(item -> item.getCoin().equals(balance.getString("ccy"))).findFirst().ifPresent(obj ->{
                            obj.setBalance(balance.getBigDecimal("availBal"));
                        });
                    }
                    Thread.sleep(200);
                }
                balanceBusiness.saveOrUpdateBatch(allBalanceList.stream().distinct().collect(Collectors.toList()));
            }
        } catch (Exception e) {
            log.error("syncCurrencies error:", e);
        }

    }




    @Async
    public void updateCoin(List<JSONObject> items, List<OkxCoinTicker> tickers, Date now, Map<String, String> map) throws Exception {
        try {
            redisLock.lock(RedisConstants.OKX_TICKER_UPDATE_COIN,10,3,1000);

            BigDecimal usdt24h = new BigDecimal(settingService.selectSettingByKey(OkxConstants.USDT_24H));
            List<OkxCoin> okxCoins = coinBusiness.list().stream().filter(item -> item.getUnit().compareTo(BigDecimal.ZERO) > 0).collect(Collectors.toList());
            for (int i = 0; i < items.size(); i++) {
                int finalI = i;
                okxCoins.stream().filter(item -> item.getCoin().equals(tickers.get(finalI).getCoin())).findFirst().ifPresent(obj -> {
                    obj.setVolCcy24h(items.get(finalI).getBigDecimal("vol24h").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
                    obj.setVolUsdt24h(items.get(finalI).getBigDecimal("volCcy24h").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
                    obj.setHightest(items.get(finalI).getBigDecimal("high24h").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                    obj.setLowest(items.get(finalI).getBigDecimal("low24h").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                    obj.setRise((tickers.get(finalI).getIns().compareTo(BigDecimal.ZERO) >= 0));
                    obj.setUpdateTime(now);
                    //交易额低于配置值-关闭交易
                    if (usdt24h.compareTo(obj.getVolUsdt24h()) > 0) {
                        obj.setStatus(CoinStatusEnum.CLOSE.getStatus());
                    } else {
                        obj.setStatus(CoinStatusEnum.OPEN.getStatus());
                    }
                    if (obj.getCoin().equalsIgnoreCase("BTC")) {
                        obj.setBtcIns(tickers.get(finalI).getIns());
                    }
                    obj.setStandard(coinBusiness.calculateStandard(tickers.get(finalI)));
                });
            }
            boolean update = coinBusiness.updateList(okxCoins);

            if (update == true) {
                //更新涨跌数据
                accountBusiness.list().stream().forEach(item -> {
                    List<OkxSetting> okxSettings =   accountBusiness.listByAccountId(item.getId());
                    this.refreshRiseCount(okxCoins, now, item.getId(), okxSettings);
                    tradeBusiness.trade( okxCoins, tickers, okxSettings,accountBusiness.getAccountMap(item));
                });
            }
            redisLock.releaseLock(RedisConstants.OKX_TICKER_UPDATE_COIN);
        } catch (Exception e) {
            log.error("updateCoin error",e);
            redisLock.releaseLock(RedisConstants.OKX_TICKER_UPDATE_COIN);
        }
    }


    /**
     * 更新coin涨跌
     * @param okxCoins
     * @param now
     */
    public void refreshRiseCount(List<OkxCoin> okxCoins, Date now, Integer accountId,List<OkxSetting> settingList){
        Integer riseCount = okxCoins.stream().filter(item -> (item.isRise() == true)).collect(Collectors.toList()).size();
        BigDecimal risePercent = new BigDecimal(riseCount).divide(new BigDecimal(okxCoins.size()), 4,BigDecimal.ROUND_DOWN);
        BigDecimal lowPercent = BigDecimal.ONE.subtract(risePercent).setScale(4);
        RiseDto riseDto = redisService.getCacheObject(tradeBusiness.getCacheMarketKey(accountId));
        if (riseDto == null) {
            riseDto = new RiseDto();
            String modeType = settingList.stream().filter(item -> item.getSettingKey().equals(OkxConstants.MODE_TYPE)).findFirst().get().getSettingValue();
            riseDto.setModeType(modeType);
        }
        riseDto.setRiseCount(riseCount);
        riseDto.setRisePercent(risePercent);
        if (risePercent.compareTo(riseDto.getHighest()) > 0) {
            riseDto.setHighest(risePercent);
        }
        riseDto.setLowCount(okxCoins.size() - riseCount);
        if (lowPercent.compareTo(riseDto.getLowPercent()) > 0) {
            riseDto.setLowest(lowPercent);
        }
        riseDto.setLowPercent(lowPercent);
        for (OkxCoin okxCoin:okxCoins) {
            if (okxCoin.getCoin().equalsIgnoreCase("BTC")) {
                riseDto.setBTCIns(okxCoin.getBtcIns());
            }
        }
        long diff = DateUtil.diffSecond(now, DateUtil.getMaxTime(now));
        redisService.setCacheObject(tradeBusiness.getCacheMarketKey(accountId), riseDto, diff, TimeUnit.SECONDS);
    }

}
