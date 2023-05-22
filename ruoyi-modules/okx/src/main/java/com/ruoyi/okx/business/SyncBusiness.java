package com.ruoyi.okx.business;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.ruoyi.common.core.enums.Status;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.CoinStatusEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class SyncBusiness {

    private static final Logger log = LoggerFactory.getLogger(SyncBusiness.class);

    @Resource
    private AccountBusiness accountBusiness;

    @Resource
    private AccountBalanceBusiness balanceBusiness;

    @Resource
    private CoinBusiness coinBusiness;

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private SellRecordBusiness sellRecordBusiness;

    @Resource
    private CommonBusiness commonBusiness;

    @Resource
    private SyncCoinBusiness syncCoinCountBusiness;

    @Resource
    private TickerBusiness tickerBusiness;

    @Resource
    private RedisService redisService;



    @Async
    public void syncCurrencies() {
        List<OkxCoin> saveCoins = Lists.newArrayList();
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
            List<OkxAccount> accounts = accountBusiness.list().stream()
                    .filter(item -> item.getStatus().intValue() == Status.OK.getCode()).collect(Collectors.toList());

            for (int i = 0; i < jsonArray.size(); i++) {
               JSONObject item = jsonArray.getJSONObject(i);
                OkxCoin coin = coinBusiness.getCoin(item.getString("ccy"));
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
                    coin.setCount(BigDecimal.ZERO);
                    coin.setCoin(item.getString("ccy"));
                    coin.setUpdateTime(now);
                    saveCoins.add(coin);
                }
            }

            if (CollectionUtils.isNotEmpty(saveCoins)){
                //set unit
                JSONArray unitArray = getUnit(map);
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
                    coinBusiness.loadingCache();
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
            tickerBusiness.syncTicker();
        } catch (Exception e) {
            log.error("同步ticker异常", e);
        }
    }


}
