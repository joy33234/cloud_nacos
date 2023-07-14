package com.ruoyi.okx.business;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.CacheObj;
import cn.hutool.core.lang.func.Func0;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.enums.Status;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.mapper.CoinTickerMapper;
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.Constant;
import com.ruoyi.okx.utils.DtoUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.poi.ss.formula.functions.T;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TickerBusiness extends ServiceImpl<CoinTickerMapper, OkxCoinTicker> {
    private static final Logger log = LoggerFactory.getLogger(TickerBusiness.class);

    @Resource
    private CoinTickerMapper tickerMapper;

    @Resource
    private CoinBusiness coinBusiness;

    @Resource
    private AccountBusiness accountBusiness;

    @Autowired
    private RedisLock redisLock;

    @Resource
    private RedisService redisService;

    @Resource
    private SyncCoinBusiness syncCoinBusiness;

    @Resource
    private TradeBusiness tradeBusiness;

    @Resource
    private AccountBalanceBusiness balanceBusiness;

    @Resource
    private SettingService settingService;


//
//    @Transactional(rollbackFor = Exception.class)
//    public boolean syncTicker_v3() throws ServiceException{
//        try {
//            Map<String, String> accountMap = accountBusiness.getAccountMap();
//            boolean result = redisLock.lock(RedisConstants.OKX_TICKER,30,3,5000);
//            if (result == false) {
//                log.error("syncTicker-syncTicker 获取锁异常");
//                return false;
//            }
//
//            String str = HttpUtil.getOkx("/api/v5/market/tickers?instType=SPOT", null, accountMap);
//            JSONObject json = JSONObject.parseObject(str);
//            if (json == null || !json.getString("code").equals("0")) {
//                return false;
//            }
//            List<OkxCoinTicker> dbTickerList = Lists.newArrayList();
//            Date now = new Date();
//            for (int i = 0; i <= 29; i++) {
//                LambdaQueryWrapper<OkxCoinTicker> wrapper1 = new LambdaQueryWrapper();
//                Date day =  DateUtil.addDate(now, i-29);
//                wrapper1.ge(OkxCoinTicker::getCreateTime, DateUtil.getMinTime(day));
//                wrapper1.le(OkxCoinTicker::getCreateTime, DateUtil.getMaxTime(day));
//                List<OkxCoinTicker> tempList = this.tickerMapper.selectList(wrapper1);
//                dbTickerList.addAll(tempList);
//            }
//            JSONArray jsonArray = json.getJSONArray("data");
//            List<OkxCoinTicker> tickerList = new LinkedList<>();
//            List<JSONObject> jsonObjectList = new LinkedList<>();
//            for (int i = 0; i < jsonArray.size(); i++) {
//                JSONObject item = jsonArray.getJSONObject(i);
//                OkxCoinTicker ticker = JSON.parseObject(item.toJSONString(), OkxCoinTicker.class);
//                String[] arr = item.getString("instId").split("-");
//                if (arr[1].equals("USDT")) {
//                    ticker.setCoin(arr[0]);
//                    ticker.setOpen24h(item.getBigDecimal("sodUtc8"));
//
//                    //ticker.setLast(ticker.getLast().add(ticker.getLast().multiply(new BigDecimal(0.018))));
//
//                    Optional<OkxCoinTicker> dbticker = dbTickerList.stream().filter(obj -> obj.getCoin().equals(ticker.getCoin()))
//                            .filter(obj -> obj.getCreateTime().getTime() >= DateUtil.getMinTime(now).getTime()).findFirst();
//                    if (dbticker.isPresent()) {
//                        ticker.setId((dbticker.get().getId()));
//                    } else {
//                        ticker.setCreateTime(now);
//                        //暂停止买入新发币
////                            if (tickerList1.size() == 0) {
////                                OkbCoin coin = this.coinBusiness.findOne(ticker.getCoin());
////                                if (coin == null || DateUtil.diffMins(coin.getCreateTime(), coin.getUpdateTime()) < 1)
////                                    this.tradeBusiness.buyNewCoin(ticker, accountMap);
////                            }
//                    }
//                    ticker.setAverage(ticker.getHigh24h().add(ticker.getLow24h()).divide(new BigDecimal(2), 8, RoundingMode.HALF_UP));
//                    ticker.setIns(ticker.getLast().subtract(ticker.getOpen24h()).divide(ticker.getOpen24h(), 8, RoundingMode.HALF_UP));
//                    //计算月平均值
//                    List<OkxCoinTicker> allTickerList = dbTickerList.stream().filter(obj -> obj.getCoin().equals(ticker.getCoin())).collect(Collectors.toList());
//                    if (CollectionUtils.isNotEmpty(allTickerList)) {
//                        BigDecimal monthAverage = (allTickerList.stream().map(OkxCoinTicker::getAverage).reduce(BigDecimal.ZERO, BigDecimal::add)).divide(new BigDecimal(allTickerList.size()), 8, RoundingMode.HALF_UP);
//                        ticker.setMonthAverage(monthAverage);
//                        BigDecimal monthIns = (allTickerList.stream().map(OkxCoinTicker::getIns).reduce(BigDecimal.ZERO, BigDecimal::add)).divide(new BigDecimal(allTickerList.size()), 8, RoundingMode.HALF_UP);
//                        ticker.setMonthIns(monthIns);
//                    } else {
//                        ticker.setMonthAverage(BigDecimal.ZERO);
//                        ticker.setMonthIns(BigDecimal.ZERO);
//                    }
//                    ticker.setUpdateTime(now);
//
//                    tickerList.add(ticker);
//                    jsonObjectList.add(item);
//                }
//            }
//            saveOrUpdateBatch(tickerList);
//            syncCoinBusiness.updateCoin(jsonObjectList, tickerList, now);
//
//            redisLock.releaseLock(RedisConstants.OKX_TICKER);
//        } catch (Exception e) {
//            log.error("syncTicker error:", e);
//            redisLock.releaseLock(RedisConstants.OKX_TICKER);
//            throw new ServiceException("syncTicker error");
//        }
//        return true;
//    }

    /**
     * new
     * @return
     * @throws ServiceException
     */
    @Async
    public void syncTicker(JSONObject item, List<OkxCoinTicker> coinTickerList, Integer riseCount, Integer fallCount, List<OkxAccount> accountList,  Map<String, List<OkxSetting>> accountSettingMap,List<OkxBuyRecord> coinBuyRecords) throws ServiceException{
        try {
            Date now = new Date();
            //遍历每个币种
            String[] arr = item.getString("instId").split("-");
            if (!arr[1].equals("USDT")) {
                return ;
            }
            //更新行情数据
            OkxCoinTicker ticker = updateTicker( item,coinTickerList);

            //更新币种数据
            OkxCoin okxCoin = syncCoinBusiness.updateCoinV2(ticker, now);
            if (okxCoin == null) {
                return;
            }
            //交易
            tradeBusiness.tradeV2(riseCount, fallCount, accountList, okxCoin, ticker, now, accountSettingMap, coinBuyRecords);

        } catch (Exception e) {
            log.error("syncTicker error:", e);
            throw new ServiceException("syncTicker error");
        }
    }

    public OkxCoinTicker updateTicker(JSONObject item, List<OkxCoinTicker> monthTickerList){
        Date now = new Date();

        OkxCoinTicker ticker = JSON.parseObject(item.toJSONString(), OkxCoinTicker.class);
        String[] arr = item.getString("instId").split("-");
        if (arr[1].equals("USDT")) {
            ticker.setCoin(arr[0]);
            ticker.setOpen24h(item.getBigDecimal("sodUtc8"));

            ticker.setAverage(ticker.getHigh24h().add(ticker.getLow24h()).divide(new BigDecimal(2), 8, RoundingMode.HALF_UP));
            ticker.setIns(ticker.getLast().subtract(ticker.getOpen24h()).divide(ticker.getOpen24h(), 8, RoundingMode.HALF_UP));
            //币种低于30天行情数据不买入
            if (CollectionUtils.isNotEmpty(monthTickerList) && monthTickerList.size() >= 29) {
                BigDecimal monthAverage = (monthTickerList.stream().map(OkxCoinTicker::getAverage).reduce(BigDecimal.ZERO, BigDecimal::add)).divide(new BigDecimal(monthTickerList.size()), 8, RoundingMode.HALF_UP);
                ticker.setMonthAverage(monthAverage);
                BigDecimal monthIns = (monthTickerList.stream().map(OkxCoinTicker::getIns).reduce(BigDecimal.ZERO, BigDecimal::add)).divide(new BigDecimal(monthTickerList.size()), 8, RoundingMode.HALF_UP);
                ticker.setMonthIns(monthIns);
            } else {
                ticker.setMonthAverage(BigDecimal.ZERO);
                ticker.setMonthIns(BigDecimal.ZERO);
            }
            ticker.setUpdateTime(now);
        }
        //更新缓存 update last five minute each day
//        if (now.getTime() > (DateUtil.getMaxTime(now).getTime() - 300000)) {
//            if (findTodayTicker(ticker.getCoin()).getUpdateTime().getTime() > DateUtil.getMinTime(now).getTime()){
                updateCache(Collections.singletonList(ticker));
//            }
//        }
        return ticker;
    }



    public List<OkxCoinTicker> findTodayTicker() {
        LambdaQueryWrapper<OkxCoinTicker> wrapper = new LambdaQueryWrapper();
        wrapper.ge(OkxCoinTicker::getCreateTime, DateUtil.getMinTime(new Date()));
        return tickerMapper.selectList(wrapper);
    }

    @Async
    public void updateCache(List<OkxCoinTicker> tickerList) {
        for (OkxCoinTicker ticker : tickerList) {
            redisService.setCacheObject(CacheConstants.OKX_TICKER_KEY + ticker.getCoin(), ticker);
        }
    }


    public List<OkxCoinTicker> getTickerCache() {
        List<OkxCoinTicker> list = Lists.newArrayList();
        Collection<String> keys = redisService.keys(CacheConstants.OKX_TICKER_KEY + "*");
        for (String key:keys) {
            list.add(redisService.getCacheObject(key));
        }
        return list;
    }

    public OkxCoinTicker getTickerCache(String coin) {
        return redisService.getCacheObject(CacheConstants.OKX_TICKER_KEY + coin);
    }


    @Transactional(rollbackFor = Exception.class)
    public void syncTickerDb() {
        Date now = new Date();
        List<OkxCoinTicker> todayCacheTickers = getTickerCache();
        List<OkxCoinTicker> todayDbTickers = findTodayTicker();
        for (OkxCoinTicker cacheTicker: todayCacheTickers) {
            todayDbTickers.stream().filter(item -> item.getCoin().equals(cacheTicker.getCoin())).findFirst().ifPresent(obj -> {
                cacheTicker.setId(obj.getId());
                cacheTicker.setUpdateTime(now);
            });
            cacheTicker.setCreateTime(now);
        }
        saveOrUpdateBatch(todayCacheTickers);
    }


}
