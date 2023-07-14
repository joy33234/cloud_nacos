package com.ruoyi.okx.business;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.enums.Status;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.CoinStatusEnum;
import com.ruoyi.okx.enums.ModeTypeEnum;
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.Constant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.apache.commons.collections4.CollectionUtils;
import springfox.documentation.swagger.web.UiConfigurationBuilder;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
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

    @Value("${okx.newRedis}")
    public boolean newRedis;

    public void syncOkxBalance() {
        accountBusiness.list().stream().filter(item -> item.getStatus().intValue() == Status.OK.getCode()).forEach(account -> {
            syncAccountBalance(account);
        });
    }

    @Async
    public void syncAccountBalance(OkxAccount account) {
        try {
            Date now = new Date();
            List<OkxCoin> okxCoins = Lists.newArrayList();
            List<OkxAccountBalance> balanceList = Lists.newArrayList();
            Map<String, String> map = accountBusiness.getAccountMap(account);
            int pages = 0;

            List<OkxAccountBalance> DbBalanceList = balanceBusiness.list(new OkxAccountBalanceDO(null,null,null,account.getId(),null));
            if (CollectionUtils.isEmpty(DbBalanceList)) {
                okxCoins = coinBusiness.selectCoinList(null);
                pages = getPages(okxCoins.size());
            } else {
                pages = getPages(DbBalanceList.size());
            }

            String coins = "";
            //帐户币种数量
            for (int i = 0; i < pages; i++) {
                List<OkxAccountBalance> subBalanceList = Lists.newArrayList();
                if (CollectionUtils.isEmpty(DbBalanceList)) {
                    List<OkxCoin> subCoinList = okxCoins.subList(i * 20, ((i + 1) * 20 <= okxCoins.size()) ? ((i + 1) * 20) : okxCoins.size());
                    coins = StringUtils.join(subCoinList.stream().map(OkxCoin::getCoin).collect(Collectors.toList()), ",");
                    for (OkxCoin coin:subCoinList) {
                        subBalanceList.add(new OkxAccountBalance(null,account.getId(),account.getName(),coin.getCoin(),null));
                    }
                } else {
                    subBalanceList = DbBalanceList.subList(i * 20, ((i + 1) * 20 <= DbBalanceList.size()) ? ((i + 1) * 20) : DbBalanceList.size());
                    coins = StringUtils.join(subBalanceList.stream().map(OkxAccountBalance::getCoin).collect(Collectors.toList()), ",");
                }
                balanceList.addAll(balanceBusiness.getBalance(coins, map, subBalanceList, now));
                Thread.sleep(500);
            }
            balanceBusiness.saveOrUpdateBatch(balanceList.stream().distinct().collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("syncOkxBalance error:", e);
        }
    }

    private int getPages(int sum) {
        int pages = sum / 20;
        if (sum % 20 != 0) {
            pages++;
        }
        return pages;
    }

//    private List<OkxAccountBalance> getBalance(String coinsStr, Map<String, String> map,List<OkxAccountBalance> subList, Date now) {
//        String str = HttpUtil.getOkx("/api/v5/account/balance?ccy=" + coinsStr, null, map);
//        JSONObject json = JSONObject.parseObject(str);
//        if (json == null || !json.getString("code").equals("0")) {
//            log.error("str:{}", str);
//            throw new ServiceException(str);
//        }
//        JSONObject data = json.getJSONArray("data").getJSONObject(0);
//        JSONArray detail = data.getJSONArray("details");
//        for (OkxAccountBalance okxAccountBalance:subList) {
//            for (int j = 0; j < detail.size(); j++) {
//                JSONObject balance = detail.getJSONObject(j);
//                if (okxAccountBalance.getCoin().equalsIgnoreCase(balance.getString("ccy"))) {
//                    okxAccountBalance.setBalance(balance.getBigDecimal("availBal"));
//                }
//                okxAccountBalance.setUpdateTime(now);
//            }
//        }
//        return subList;
//    }

//
//    public void updateCoin(List<JSONObject> items, List<OkxCoinTicker> tickers, Date now) throws Exception {
//        try {
//            BigDecimal usdt24h = new BigDecimal(settingService.selectSettingByKey(OkxConstants.USDT_24H));
//            List<OkxCoin> okxCoins = coinBusiness.list().stream().filter(item -> item.getUnit().compareTo(BigDecimal.ZERO) > 0).collect(Collectors.toList());
//            for (int i = 0; i < items.size(); i++) {
//                int finalI = i;
//                okxCoins.stream().filter(item -> item.getCoin().equals(tickers.get(finalI).getCoin())).findFirst().ifPresent(obj -> {
//                    obj.setVolCcy24h(items.get(finalI).getBigDecimal("vol24h").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
//                    obj.setVolUsdt24h(items.get(finalI).getBigDecimal("volCcy24h").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
//                    obj.setHightest(items.get(finalI).getBigDecimal("high24h").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
//                    obj.setLowest(items.get(finalI).getBigDecimal("low24h").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
//                    obj.setRise((tickers.get(finalI).getIns().compareTo(BigDecimal.ZERO) >= 0));
//                    obj.setUpdateTime(now);
//                    //交易额低于配置值-关闭交易
//                    if (usdt24h.compareTo(obj.getVolUsdt24h()) > 0) {
//                        obj.setStatus(CoinStatusEnum.CLOSE.getStatus());
//                    } else {
//                        obj.setStatus(CoinStatusEnum.OPEN.getStatus());
//                    }
//                    if (obj.getCoin().equalsIgnoreCase("BTC")) {
//                        obj.setBtcIns(tickers.get(finalI).getIns());
//                    }
//                    obj.setStandard(coinBusiness.calculateStandard(tickers.get(finalI)));
//                });
//            }
//            //更新缓存
//            coinBusiness.updateCache(okxCoins);
//
//            //更新行情涨跌数据
//            accountBusiness.list().stream()
//                    .filter(item -> item.getStatus().intValue() == Status.OK.getCode()).forEach(item -> {
//                List<OkxSetting> okxSettings =   accountBusiness.listByAccountId(item.getId());
//                //更新行情缓存
//                if (this.refreshRiseCount(okxCoins, now, item.getId(), okxSettings) == true) {
//                    //coin set balance
//                    List<OkxAccountBalance> balances = balanceBusiness.list(new OkxAccountBalanceDO(null,null,null,item.getId(),null));
//                    List<OkxCoin> accountCoins =  okxCoins;
//                    accountCoins.stream().forEach(okxCoin -> {
//                        balances.stream().filter(obj -> obj.getCoin().equals(okxCoin.getCoin())).findFirst().ifPresent( balance -> {
//                            okxCoin.setCount(balance.getBalance());
//                        });
//                    });
//                    tradeBusiness.trade(accountCoins, tickers, okxSettings, accountBusiness.getAccountMap(item));
//                }
//            });
//        } catch (Exception e) {
//            log.error("updateCoin error",e);
//            throw new Exception(e.getMessage());
//        }
//    }

    public OkxCoin updateCoinV2(OkxCoinTicker ticker, Date now) throws Exception {
        try {
            BigDecimal usdt24h = new BigDecimal(settingService.selectSettingByKey(OkxConstants.USDT_24H));

            OkxCoin obj = coinBusiness.getCoinCache(ticker.getCoin());
            if (obj.getUnit().compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }

            obj.setVolCcy24h(ticker.getVol24h().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
            obj.setVolUsdt24h(ticker.getVolCcy24h().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
            obj.setHightest(ticker.getHigh24h().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
            obj.setLowest(ticker.getLow24h().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
            obj.setRise((ticker.getIns().compareTo(BigDecimal.ZERO) >= 0));
            obj.setUpdateTime(now);
            //交易额低于配置值-关闭交易
            if (usdt24h.compareTo(obj.getVolUsdt24h()) > 0) {
                obj.setStatus(CoinStatusEnum.CLOSE.getStatus());
            } else {
                obj.setStatus(CoinStatusEnum.OPEN.getStatus());
            }
            if (obj.getCoin().equalsIgnoreCase("BTC")) {
                obj.setBtcIns(ticker.getIns());
            }
            BigDecimal standard = coinBusiness.calculateStandard(ticker);
            if (standard.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            obj.setStandard(standard);

            //更新缓存 update last five minute each day
            if (now.getTime() > (DateUtil.getMaxTime(now).getTime() - 300000)) {
                if (coinBusiness.getCoin(ticker.getCoin()).getUpdateTime().getTime() > DateUtil.getMinTime(now).getTime()){
                    coinBusiness.updateCache(Collections.singletonList(obj));
                }
            }
            return obj;
        } catch (Exception e) {
            log.error("updateCoin error",e);
            throw new Exception(e.getMessage());
        }
    }


//
//    public List<OkxCoin> updateCoinV2(List<OkxCoinTicker> tickers, Date now) throws Exception {
//        try {
//            BigDecimal usdt24h = new BigDecimal(settingService.selectSettingByKey(OkxConstants.USDT_24H));
//
//            List<OkxCoin> okxCoins = coinBusiness.getCoinCache().stream().filter(item -> item.getUnit().compareTo(BigDecimal.ZERO) > 0).collect(Collectors.toList());
//            //coinBusiness.list().stream().filter(item -> item.getUnit().compareTo(BigDecimal.ZERO) > 0).collect(Collectors.toList());
//
//            for (OkxCoinTicker ticker: tickers) {
//                okxCoins.stream().filter(item -> item.getCoin().equals(ticker.getCoin())).findFirst().ifPresent(obj -> {
//                    obj.setVolCcy24h(ticker.getVol24h().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
//                    obj.setVolUsdt24h(ticker.getVolCcy24h().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
//                    obj.setHightest(ticker.getHigh24h().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
//                    obj.setLowest(ticker.getLow24h().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
//                    obj.setRise((ticker.getIns().compareTo(BigDecimal.ZERO) >= 0));
//                    obj.setUpdateTime(now);
//                    //交易额低于配置值-关闭交易
//                    if (usdt24h.compareTo(obj.getVolUsdt24h()) > 0) {
//                        obj.setStatus(CoinStatusEnum.CLOSE.getStatus());
//                    } else {
//                        obj.setStatus(CoinStatusEnum.OPEN.getStatus());
//                    }
//                    if (obj.getCoin().equalsIgnoreCase("BTC")) {
//                        obj.setBtcIns(ticker.getIns());
//                    }
//                    obj.setStandard(coinBusiness.calculateStandard(ticker));
//                });
//            }
//            okxCoins = okxCoins.stream()
//                    .filter(item -> item.getStandard().compareTo(BigDecimal.ZERO) > 0 ).collect(Collectors.toList());
//            //更新缓存
//            coinBusiness.updateCache(okxCoins);
//            return okxCoins;
//        } catch (Exception e) {
//            log.error("updateCoin error",e);
//            throw new Exception(e.getMessage());
//        }
//    }


    /**
     * 更新coin涨跌
     * @param okxCoins
     * @param now
     */
    public boolean refreshRiseCount(List<OkxCoin> okxCoins, Date now, Integer accountId,List<OkxSetting> settingList){
        String modeType = settingList.stream().filter(item -> item.getSettingKey().equals(OkxConstants.MODE_TYPE)).findFirst().get().getSettingValue();
        if (!modeType.equals(ModeTypeEnum.MARKET.getValue())) {
            return true;
        }

        if ((now.getTime() - DateUtil.getMinTime(now).getTime() < 300000) || newRedis == true) {
            redisService.setCacheObject(tradeBusiness.getCacheMarketKey(accountId), new RiseDto());
            return false;
        }

        String key = tradeBusiness.getCacheMarketKey(accountId);
        RiseDto riseDto = redisService.getCacheObject(key);
        if (riseDto == null) {//redis异常 TODO
            return false;
        }

        Integer riseCount = okxCoins.stream().filter(item -> (item.isRise() == true)).collect(Collectors.toList()).size();
        BigDecimal risePercent = new BigDecimal(riseCount).divide(new BigDecimal(okxCoins.size()), 4,BigDecimal.ROUND_DOWN);
        BigDecimal lowPercent = BigDecimal.ONE.subtract(risePercent).setScale(Constant.OKX_BIG_DECIMAL);

        riseDto.setRiseCount(riseCount);
        riseDto.setRisePercent(risePercent);
        if (risePercent.compareTo(riseDto.getHighest()) > 0) {
            riseDto.setHighest(risePercent);
        }
        riseDto.setLowCount(okxCoins.size() - riseCount);
        riseDto.setLowPercent(lowPercent);
        if (lowPercent.compareTo(riseDto.getLowPercent()) > 0) {
            riseDto.setLowest(lowPercent);
        }
        for (OkxCoin okxCoin:okxCoins) {
            if (okxCoin.getCoin().equalsIgnoreCase("BTC")) {
                riseDto.setBTCIns(okxCoin.getBtcIns());
            }
        }
        redisService.setCacheObject(tradeBusiness.getCacheMarketKey(accountId), riseDto);
        return true;
    }


    /**
     * 更新coin涨跌
     * @param okxCoins
     * @param now
     */
    public RiseDto refreshRiseCountV2(List<OkxCoin> okxCoins, Date now, OkxAccount okxAccount,List<OkxSetting> settingList){
        String modeType = settingList.stream().filter(item -> item.getSettingKey().equals(OkxConstants.MODE_TYPE)).findFirst().get().getSettingValue();
        if (!modeType.equals(ModeTypeEnum.MARKET.getValue())) {
            return new RiseDto();
        }
        String key = tradeBusiness.getCacheMarketKey(okxAccount.getId());

        if ((now.getTime() - DateUtil.getMinTime(now).getTime() < 300000) || newRedis == true) {
            RiseDto riseDto = new RiseDto();
            riseDto.setAccountId(okxAccount.getId());
            riseDto.setAccountName(okxAccount.getName());
            riseDto.setApikey(okxAccount.getApikey());
            riseDto.setSecretkey(okxAccount.getSecretkey());
            riseDto.setPassword(okxAccount.getPassword());
            redisService.setCacheObject(key, riseDto);
            return null;
        }

        RiseDto riseDto = redisService.getCacheObject(key);
        if (riseDto == null) {//redis异常 TODO
            return null;
        }

        Integer riseCount = okxCoins.stream().filter(item -> (item.isRise() == true)).collect(Collectors.toList()).size();
        BigDecimal risePercent = new BigDecimal(riseCount).divide(new BigDecimal(okxCoins.size()), 4,BigDecimal.ROUND_DOWN);
        BigDecimal lowPercent = BigDecimal.ONE.subtract(risePercent).setScale(Constant.OKX_BIG_DECIMAL);

        riseDto.setRiseCount(riseCount);
        riseDto.setRisePercent(risePercent);
        if (risePercent.compareTo(riseDto.getHighest()) > 0) {
            riseDto.setHighest(risePercent);
        }
        riseDto.setLowCount(okxCoins.size() - riseCount);
        riseDto.setLowPercent(lowPercent);
        if (lowPercent.compareTo(riseDto.getLowPercent()) > 0) {
            riseDto.setLowest(lowPercent);
        }
        for (OkxCoin okxCoin:okxCoins) {
            if (okxCoin.getCoin().equalsIgnoreCase("BTC")) {
                riseDto.setBTCIns(okxCoin.getBtcIns());
            }
        }
        redisService.setCacheObject(key, riseDto);
        return riseDto;
    }

}
