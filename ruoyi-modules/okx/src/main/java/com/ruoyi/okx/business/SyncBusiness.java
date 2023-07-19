package com.ruoyi.okx.business;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.enums.Status;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.CoinStatusEnum;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.Constant;
import com.ruoyi.okx.utils.DtoUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.http.client.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class SyncBusiness {

    private static final Logger log = LoggerFactory.getLogger(SyncBusiness.class);

    private Cache<String, List<OkxCoinTicker>> monthTickersCache = CacheUtil.newLRUCache(30);

    @Resource
    private AccountBusiness accountBusiness;

    @Resource
    private CoinBusiness coinBusiness;

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private SellRecordBusiness sellRecordBusiness;

    @Resource
    private SyncCoinBusiness syncCoinCountBusiness;

    @Resource
    private TickerBusiness tickerBusiness;

    @Resource
    private SettingService settingService;

    @Resource
    private TradeBusiness tradeBusiness;

    @Async
    public void syncCurrencies() {
        List<OkxCoin> saveCoins = Lists.newArrayList();
        log.info("syncCurrencies-start");

        try {
            OkxAccount account = accountBusiness.list().get(0);
            Map<String, String> map = accountBusiness.getAccountMap(account);
            String str = HttpUtil.getOkx("/api/v5/asset/currencies", null, map);
            JSONObject json = JSONObject.parseObject(str);
            if (json == null || !json.getString("code").equals("0")) {
                log.error("获取币种信息异常 str:{}", str);
                return;
            }
            Date now = new Date();
            JSONArray jsonArray = json.getJSONArray("data");

            for (int i = 0; i < jsonArray.size(); i++) {
               JSONObject item = jsonArray.getJSONObject(i);
                OkxCoin coin = coinBusiness.findOne(item.getString("ccy"));
                if (coin == null) {
                    coin = new OkxCoin();
                    coin.setCreateTime(now);
                    coin.setLowest(BigDecimal.ZERO);
                    coin.setHightest(BigDecimal.ZERO);
                    coin.setUnit(BigDecimal.ZERO);
                    coin.setRise(false);
                    coin.setStandard(BigDecimal.ZERO);
                    coin.setStatus(CoinStatusEnum.OPEN.getStatus());
                    coin.setVolCcy24h(BigDecimal.ZERO);
                    coin.setVolUsdt24h(BigDecimal.ZERO);
                    coin.setBalance(BigDecimal.ZERO);
                    coin.setCoin(item.getString("ccy"));
                    coin.setUpdateTime(now);
                    saveCoins.add(coin);
                }
            }
            log.info("saveCoins：{}",JSON.toJSONString(saveCoins));
            if (CollectionUtils.isNotEmpty(saveCoins)){
                //set unit
                JSONArray unitArray = getUnit(map);
                log.info("unitArray：{}",JSON.toJSONString(unitArray));
                for (OkxCoin okxCoin:saveCoins) {
                    for (int i = 0; i < unitArray.size(); i++) {
                        JSONObject item = unitArray.getJSONObject(i);
                        String[] arr = item.getString("instId").split("-");
                        if (arr[1].equals("USDT") && arr[0].equalsIgnoreCase(okxCoin.getCoin())) {
                            okxCoin.setUnit(item.getBigDecimal("minSz"));
                        }
                    }
                }
                if(coinBusiness.saveBatch(saveCoins.stream().distinct().collect(Collectors.toList()))) {
                    coinBusiness.updateCache(saveCoins.stream().distinct().collect(Collectors.toList()));
                }
            }
        } catch (Exception e) {
            log.error("syncCurrencies error:", e);
        }
    }

    private JSONArray getUnit(Map<String, String> map) {
        String str = HttpUtil.getOkx("/api/v5/public/instruments?instType=SPOT", null, map);
        JSONObject json = JSONObject.parseObject(str);
        if (json == null || !json.getString("code").equals("0")) {
            log.error("str:{}", str);
            return new JSONArray();
        }
        return json.getJSONArray("data");
    }

    @Async
    public void syncCoinBalance() {
        syncCoinCountBusiness.syncOkxBalance();
    }

    @Async
    public void syncCoin() {
        coinBusiness.syncCoinDb();
    }

    @Async
    public void initCoinTurnOver() {
        coinBusiness.initTurnOver();
    }

    @Async
    public void syncBuyOrder() throws ServiceException {
        try {
            List<OkxAccount> accounts = accountBusiness.list().stream()
                    .filter(item -> item.getStatus().intValue() == Status.OK.getCode()).collect(Collectors.toList());;
            for (OkxAccount account:accounts) {
                Map<String, String> map = accountBusiness.getAccountMap(account);
                buyRecordBusiness.syncBuyOrder(map);
            }
        } catch (Exception e) {
            log.error("同步订单异常", e);
            throw new ServiceException("同步订单异常");
        }
    }


    @Async
    public void syncBuyOrderFee() throws ServiceException {
        try {
            List<OkxAccount> accounts = accountBusiness.list().stream()
                    .filter(item -> item.getStatus().intValue() == Status.OK.getCode()).collect(Collectors.toList());;
            for (OkxAccount account:accounts) {
                Map<String, String> map = accountBusiness.getAccountMap(account);
                buyRecordBusiness.syncOrderFeeAgain(map);
            }
        } catch (Exception e) {
            log.error("同步订单异常", e);
            throw new ServiceException("同步订单异常");
        }
    }



    @Async
    public void syncSellOrder() {
        try {
            List<OkxAccount> accounts = accountBusiness.list().stream()
                    .filter(item -> item.getStatus().intValue() == Status.OK.getCode()).collect(Collectors.toList());;
            for (OkxAccount account:accounts) {
                Map<String, String> map = accountBusiness.getAccountMap(account);
                sellRecordBusiness.syncSellOrderStatus(map);
            }
        } catch (Exception e) {
            log.error("同步订单异常", e);
        }
    }

    @Async
    public void syncTicker() {
        try {
            Long start = System.currentTimeMillis();
            Date now = new Date();

            //当天前5分钟禁止交易
            if ((now.getTime() - DateUtil.getMinTime(now).getTime() < 300000) ) {
                return ;
            }

            List<OkxAccount> accountList = accountBusiness.list().stream().filter(item -> item.getStatus().intValue() == Status.OK.getCode()).collect(Collectors.toList());
            Map<String, String> accountMap = accountBusiness.getAccountMap(accountList.get(0));

            String tickerRes = HttpUtil.getOkx("/api/v5/market/tickers?instType=SPOT", null, accountMap);
            JSONObject json = JSONObject.parseObject(tickerRes);
            if (json == null || !json.getString("code").equals("0")) {
                log.error("获取行情数据异常:{}",tickerRes);
                return ;
            }
            //整体行情
            int riseCount = 0 ;
            int fallCount = 0 ;
            JSONArray jsonArray = json.getJSONArray("data");
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                OkxCoinTicker ticker = JSON.parseObject(item.toJSONString(), OkxCoinTicker.class);
                String[] arr = item.getString("instId").split("-");
                if (arr[1].equals("USDT")) {
                    if ( ticker.getLast().subtract(ticker.getOpen24h()).divide(ticker.getOpen24h(), 8, RoundingMode.HALF_UP).compareTo(BigDecimal.ZERO) >= 0 ) {
                        riseCount++;
                    } else {
                        fallCount++;
                    }
                }
            }

            Map<String, List<OkxSetting>> accountSettingMap = new ConcurrentHashMap<>(accountList.size());
            List<RiseDto> riseDtos = Lists.newArrayList();
            for (OkxAccount account: accountList) {
                accountSettingMap.put(Constant.OKX_ACCOUNT_SETTING+account.getId(), settingService.selectSettingByIds(DtoUtils.StringToLong(account.getSettingIds().split(","))));
                RiseDto riseDto = tradeBusiness.refreshRiseCountV2(riseCount, fallCount, now, account, accountSettingMap.get(Constant.OKX_ACCOUNT_SETTING+account.getId()));
                if (ObjectUtils.isNotEmpty(riseDto)) {
                    riseDtos.add(riseDto);
                }
            }
            if (CollectionUtils.isEmpty(riseDtos)) {
                log.error("更新整体行情数据异常");
                return;
            }


            //近期29天行情数据
            List<OkxCoinTicker> monthTickerList = org.apache.commons.compress.utils.Lists.newArrayList();

            for (int i = 0; i < 30; i++) {
                Date day =  DateUtil.getMinTime(DateUtil.addDate(now, i-30));

                List<OkxCoinTicker> dayTickers = monthTickersCache.get(day.toString());
                if (CollectionUtils.isEmpty(dayTickers)) {
                    LambdaQueryWrapper<OkxCoinTicker> wrapper1 = new LambdaQueryWrapper<>();
                    wrapper1.ge(OkxCoinTicker::getCreateTime, DateUtil.getMinTime(day));
                    wrapper1.le(OkxCoinTicker::getCreateTime, DateUtil.getMaxTime(day));
                    dayTickers = tickerBusiness.list(wrapper1);
                    if (CollectionUtils.isNotEmpty(dayTickers)) {
                        monthTickersCache.put(day.toString(),dayTickers);
                    }
                }
                if (CollectionUtils.isNotEmpty(dayTickers)) {
                    monthTickerList.addAll(dayTickers);
                }
            }

            List<OkxBuyRecord> successBuyRecords = getPageDate();
            OkxCoin okxCoin = coinBusiness.findOne("BTC");
            Integer diffMin = DateUtil.diffMins(okxCoin.getUpdateTime(), now);

            //遍历每个币种
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                String[] arr = item.getString("instId").split("-");
                if (!arr[1].equals("USDT")) {
                    continue;
                }
                String coin = arr[0];
                List<OkxBuyRecord> coinBuyRecords = successBuyRecords.stream().filter(record -> record.getCoin().equals(coin)).collect(Collectors.toList());
                List<OkxCoinTicker> coinTickerList = monthTickerList.stream().filter(okxCoinTicker -> okxCoinTicker.getCoin().equals(coin)).collect(Collectors.toList());
                tickerBusiness.syncTicker(item,coinTickerList, accountList, accountSettingMap, coinBuyRecords,diffMin >= 3, riseDtos);
            }
            log.info("syncTicker_time :{}", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("syncTicker error:", e);
            throw new ServiceException("syncTicker error");
        }
    }

    @Async
    public void syncTickerDb() {
        tickerBusiness.syncTickerDb();
    }

    private List<OkxBuyRecord> getPageDate() {
        List<OkxBuyRecord> buyRecords = Lists.newArrayList();

        int page = 1;
        PageHelper.startPage(page, 30, "create_time");

        List<OkxBuyRecord> accountBuyRecords = buyRecordBusiness.findPendAndSucRecord();
        buyRecords.addAll(accountBuyRecords);
        Integer pages = new PageInfo(accountBuyRecords).getPages();
        while (pages > page) {
            page++;
            PageHelper.startPage(page, 30, "create_time");
            List<OkxBuyRecord> tempRecords = buyRecordBusiness.findPendAndSucRecord();
            buyRecords.addAll(tempRecords);
        }
        return buyRecords;
    }

    @Async
    public void init() {
        try {
            coinBusiness.clearCoinMarkCache();
        } catch (Exception e) {
            log.error("同步订单异常", e);
        }
    }
}
