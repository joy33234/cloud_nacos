package com.ruoyi.okx.business;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.DateUtils;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.OkxCoin;
import com.ruoyi.okx.domain.OkxCoinTicker;
import com.ruoyi.okx.domain.OkxSetting;
import com.ruoyi.okx.domain.OkxStrategy;
import com.ruoyi.okx.enums.CoinStatusEnum;
import com.ruoyi.okx.mapper.CoinTickerMapper;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.service.SettingService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private TradeBusiness tradeBusiness;

    @Resource
    private AccountBusiness accountBusiness;

    @Autowired
    private RedisLock redisLock;

    @Resource
    private SettingService settingService;

    @Resource
    private StrategyBusiness strategyBusiness;

    @Resource
    private RedisService redisService;

    @Transactional(rollbackFor = Exception.class)
    public boolean syncTicker() {
        try {
            Map<String, String> accountMap = accountBusiness.getAccountMap();
            Long start = System.currentTimeMillis();
            redisLock.lock(RedisConstants.OKX_TICKER,10,3,1000);

            String str = HttpUtil.getOkx("/api/v5/market/tickers?instType=SPOT", null, accountMap);
            JSONObject json = JSONObject.parseObject(str);
            if (json == null || !json.getString("code").equals("0")) {
                return false;
            }
            Date now = new Date();
            LambdaQueryWrapper<OkxCoinTicker> wrapper1 = new LambdaQueryWrapper();
            wrapper1.ge(OkxCoinTicker::getCreateTime, DateUtil.getMinTime(now));
            wrapper1.le(OkxCoinTicker::getCreateTime, DateUtil.getMaxTime(now));
            List<OkxCoinTicker> dbTickerList = this.tickerMapper.selectList(wrapper1);

            JSONArray jsonArray = json.getJSONArray("data");
            List<OkxCoinTicker> tickerList = new LinkedList<>();
            List<JSONObject> jsonObjectList = new LinkedList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                OkxCoinTicker ticker = JSON.parseObject(item.toJSONString(), OkxCoinTicker.class);
                String[] arr = item.getString("instId").split("-");
                if (arr[1].equals("USDT")) {
                    ticker.setCoin(arr[0]);
                    ticker.setOpen24h(item.getBigDecimal("sodUtc8"));

                    Optional<OkxCoinTicker> dbticker = dbTickerList.stream().filter(obj -> obj.getCoin().equals(ticker.getCoin())).findFirst();
                    if (dbticker.isPresent()) {
                        ticker.setId((dbticker.get().getId()));
                    } else {
                        ticker.setCreateTime(now);
                        //暂停止买入新发币
//                            if (tickerList1.size() == 0) {
//                                OkbCoin coin = this.coinBusiness.findOne(ticker.getCoin());
//                                if (coin == null || DateUtil.diffMins(coin.getCreateTime(), coin.getUpdateTime()) < 1)
//                                    this.tradeBusiness.buyNewCoin(ticker, accountMap);
//                            }
                    }
                    ticker.setAverage(ticker.getHigh24h().add(ticker.getLow24h()).divide(new BigDecimal(2), 8, RoundingMode.HALF_UP));
                    ticker.setIns(ticker.getLast().subtract(ticker.getOpen24h()).divide(ticker.getOpen24h(), 8, RoundingMode.HALF_UP));
                    //计算月平均值
                    LambdaQueryWrapper<OkxCoinTicker> wrapper = new LambdaQueryWrapper();
                    wrapper.eq((null != ticker.getCoin()), OkxCoinTicker::getCoin, ticker.getCoin());
                    wrapper.ge(OkxCoinTicker::getCreateTime, DateUtil.getMinTime(DateUtil.addDate(new Date(), -30)));
                    List<OkxCoinTicker> allTickerList = this.tickerMapper.selectList(wrapper).stream().filter(o -> o.getCoin().equals(ticker.getCoin())).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(allTickerList)) {
                        BigDecimal monthAverage = (allTickerList.stream().map(OkxCoinTicker::getAverage).reduce(BigDecimal.ZERO, BigDecimal::add)).divide(new BigDecimal(allTickerList.size()), 8, RoundingMode.HALF_UP);
                        ticker.setMonthAverage(monthAverage);

                        BigDecimal monthIns = (allTickerList.stream().map(OkxCoinTicker::getIns).reduce(BigDecimal.ZERO, BigDecimal::add)).divide(new BigDecimal(allTickerList.size()), 8, RoundingMode.HALF_UP);
                        ticker.setMonthIns(monthIns);
                    } else {
                        ticker.setMonthAverage(BigDecimal.ZERO);
                        ticker.setMonthIns(BigDecimal.ZERO);
                    }
                    ticker.setUpdateTime(now);

                    tickerList.add(ticker);
                    jsonObjectList.add(item);
                }
            }
            saveOrUpdateBatch(tickerList);

            updateCoin(jsonObjectList, tickerList, now, accountMap);
            redisLock.releaseLock(RedisConstants.OKX_TICKER);
            System.out.println("执行完时间:" + (System.currentTimeMillis()-start));
        } catch (Exception e) {
            log.error("syncTicker error:", e);
            redisLock.releaseLock(RedisConstants.OKX_TICKER);
        }
        return true;
    }

    public void updateCoin(List<JSONObject> items, List<OkxCoinTicker> tickers, Date now, Map<String, String> map) throws Exception {

        BigDecimal usdt24h = new BigDecimal(settingService.selectSettingByKey(OkxConstants.USDT_24H));

        List<OkxCoin> okxCoins = coinBusiness.list();

        for (int i = 0; i < items.size(); i++) {
            int finalI = i;
            okxCoins.stream().filter(item -> item.getCoin().equals(tickers.get(finalI).getCoin())).findFirst().ifPresent(obj -> {
                obj.setVolCcy24h(items.get(finalI).getBigDecimal("vol24h"));
                obj.setVolUsdt24h(items.get(finalI).getBigDecimal("volCcy24h"));
                obj.setHightest(items.get(finalI).getBigDecimal("high24h").setScale(8, RoundingMode.HALF_UP));
                obj.setLowest(items.get(finalI).getBigDecimal("low24h").setScale(8, RoundingMode.HALF_UP));
                obj.setRise((tickers.get(finalI).getIns().compareTo(BigDecimal.ZERO) >= 0));
                obj.setUpdateTime(now);
                //交易额低于配置值-关闭交易
                if (usdt24h.compareTo(obj.getVolUsdt24h()) > 0) {
                    obj.setStatus(CoinStatusEnum.CLOSE.getStatus());
                } else if (obj.getStatus().intValue() == CoinStatusEnum.CLOSE.getStatus().intValue()) {
                    obj.setStatus(CoinStatusEnum.OPEN.getStatus());
                }
                obj.setStandard(coinBusiness.calculateStandard(tickers.get(finalI)));
            });
        }
        //更新涨跌数据
        refreshRiseCount(okxCoins, now);

        boolean update = coinBusiness.updateList(okxCoins);
        if (update == true) {
            tradeBusiness.trade( okxCoins, tickers, map);
        }
    }

    /**
     * 更新coin涨跌
     * @param okxCoins
     * @param now
     */
    private void refreshRiseCount(List<OkxCoin> okxCoins, Date now){
        RiseDto riseDto = redisService.getCacheObject(RedisConstants.OKX_TICKER_MARKET);

        Integer riseCount = okxCoins.stream().filter(item -> (item.isRise() == true)).collect(Collectors.toList()).size();
        BigDecimal risePercent = new BigDecimal(riseCount).divide(new BigDecimal(okxCoins.size()), 4,BigDecimal.ROUND_DOWN);
        BigDecimal lowPercent = BigDecimal.ONE.subtract(risePercent).setScale(4);

        if (riseDto == null) {
            if (new BigDecimal(OkxConstants.MARKET_RISE_BUY_PERCENT).compareTo(risePercent) > 0
                    || new BigDecimal(OkxConstants.MARKET_LOW_BUY_PERCENT).compareTo(lowPercent) > 0){
                return;
            }
            riseDto = new RiseDto();
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
        long diff = DateUtil.diffSecond(now, DateUtil.getMaxTime(now));
        redisService.setCacheObject(RedisConstants.OKX_TICKER_MARKET, riseDto, diff, TimeUnit.SECONDS);
    }
}
