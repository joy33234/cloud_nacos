package com.ruoyi.okx.business;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.CoinStatusEnum;
import com.ruoyi.okx.enums.OrderStatusEnum;
import com.ruoyi.okx.utils.Constant;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private RedisService redisService;

    public boolean syncCurrencies() {
        List<OkxCoin> saveCoins = Lists.newArrayList();
        try {
            OkxAccount account = accountBusiness.list().get(0);
            Map<String, String> map = accountBusiness.getAccountMap(account);
            String str = HttpUtil.getOkx("/api/v5/asset/currencies", null, map);
            JSONObject json = JSONObject.parseObject(str);
            if (json == null || !json.getString("code").equals("0")) {
                log.error("获取币种信息异常 str:{}", str);
                return false;
            }
            Date now = new Date();
            JSONArray jsonArray = json.getJSONArray("data");
            List<OkxAccount> accounts = accountBusiness.list();

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
                } else {
                    //TODO delete
                    balanceBusiness.syncBanlance(coin,accounts);
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
            log.error("saveCoins:{}", JSON.toJSONString(saveCoins));
            return false;
        }
        return true;

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

    public boolean syncCoinBalance() {
        syncCoinCountBusiness.syncOkxBalance();
        return true;
    }



    public boolean syncBuyOrder() throws ServiceException {
        try {
            List<OkxAccount> accounts = accountBusiness.list();
            for (OkxAccount account:accounts) {
                Map<String, String> map = accountBusiness.getAccountMap(account);
                buyRecordBusiness.syncBuyOrder(map);
            }
        } catch (Exception e) {
            log.error("同步订单异常", e);
            throw new ServiceException("同步订单异常");
        }
        return true;
    }




    public boolean syncSellOrder() {
        try {
            List<OkxAccount> accounts = accountBusiness.list();
            for (OkxAccount account:accounts) {
                Map<String, String> map = accountBusiness.getAccountMap(account);
                this.syncSellOrderStatus(map);
            }
        } catch (Exception e) {
            log.error("同步订单异常", e);
        }
        return true;
    }

    @Transactional(rollbackFor = {Exception.class})
    public void syncSellOrderStatus(Map<String, String> map) {
        List<OkxSellRecord> list = sellRecordBusiness.findPendings(Integer.valueOf(map.get("id")));
        Date now = new Date();
        list.stream().forEach(sellRecord -> {
            String str = HttpUtil.getOkx("/api/v5/trade/order?instId=" + sellRecord.getInstId() + "&ordId=" + sellRecord.getOkxOrderId(), null, map);
            JSONObject json = JSONObject.parseObject(str);
            if (json == null || !json.getString("code").equals("0")) {
                log.error("获取卖出订单信息异常：{}", (json == null) ? "null" : json.toJSONString());
                return;
            }
            JSONObject data = json.getJSONArray("data").getJSONObject(0);
            sellRecord.setStatus((commonBusiness.getOrderStatus(data.getString("state")) == null) ? sellRecord.getStatus() : commonBusiness.getOrderStatus(data.getString("state")));
            if (sellRecord.getStatus().equals(OrderStatusEnum.SUCCESS.getStatus())) {
                sellRecord.setFee(data.getBigDecimal("fee").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP).abs());
                boolean update = sellRecordBusiness.updateById(sellRecord);
                if (update) {
                    balanceBusiness.reduceCount(sellRecord.getCoin(), sellRecord.getAccountId(), sellRecord.getQuantity());
                    buyRecordBusiness.updateBySell(sellRecord.getBuyRecordId(), OrderStatusEnum.FINISH.getStatus());
                    return;
                }
            }
            if (sellRecord.getStatus().equals(OrderStatusEnum.FAIL.getStatus())) {
                Integer canBuuRecordId = Integer.valueOf(RandomUtil.randomInt(1000000000));
                sellRecord.setBuyRecordId(Integer.valueOf(-canBuuRecordId.intValue()));
                boolean update = sellRecordBusiness.updateById(sellRecord);
                if (update) {
                    OkxBuyRecord buyRecord = this.buyRecordBusiness.getById(sellRecord.getBuyRecordId());
                    if (buyRecord == null) {
                        log.error("{}", sellRecord.getBuyRecordId());
                        return;
                    }
                }
            }
            if (sellRecord.getStatus().equals(OrderStatusEnum.PENDING.getStatus())) {
                int diffDays = DateUtil.diffDay(DateUtil.getMinTime(sellRecord.getCreateTime()), DateUtil.getMinTime(now));
                if (diffDays > 0) {
                    Map<String, String> params = new HashMap<>(8);
                    params.put("instId", sellRecord.getInstId());
                    params.put("ordId", sellRecord.getOkxOrderId());
                    String cancelStr = HttpUtil.postOkx("/api/v5/trade/cancel-order", params, map);
                    log.info("{}", cancelStr);
                    JSONObject cancelJson = JSONObject.parseObject(cancelStr);
                    if (cancelJson == null || !cancelJson.getString("code").equals("0")) {
                        log.error("{}", JSON.toJSONString(params));
                        return;
                    }
                    JSONObject dataJSON = cancelJson.getJSONArray("data").getJSONObject(0);
                    if (dataJSON == null || !dataJSON.getString("sCode").equals("0")) {
                        log.error("{}", JSON.toJSONString(params));
                        return;
                    }
                    OkxBuyRecord buyRecord = this.buyRecordBusiness.getById(sellRecord.getBuyRecordId());
                    if (buyRecord == null) {
                        log.error("查询买入订单异常:{}", sellRecord.getBuyRecordId());
                        return;
                    }
                    sellRecord.setStatus(OrderStatusEnum.CANCEL.getStatus());
                    Integer canBuuRecordId = Integer.valueOf(RandomUtil.randomInt(1000000000));
                    sellRecord.setBuyRecordId(Integer.valueOf(-canBuuRecordId.intValue()));
                    sellRecordBusiness.updateById(sellRecord);
                    buyRecord.setStatus(OrderStatusEnum.SUCCESS.getStatus());
                    this.buyRecordBusiness.updateById(buyRecord);
                    log.info("订单买入超过1天自动撤销");
                }
            }
        });
    }
}
