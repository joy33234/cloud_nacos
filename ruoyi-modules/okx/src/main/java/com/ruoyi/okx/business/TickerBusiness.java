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
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.OkxCoinTicker;
import com.ruoyi.okx.mapper.CoinTickerMapper;
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
    private SyncCoinBusiness syncCoinBusiness;

    @Resource
    private RedisService redisService;

    @Transactional(rollbackFor = Exception.class)
    public boolean syncTicker() throws ServiceException{
        try {
            Map<String, String> accountMap = accountBusiness.getAccountMap();
            redisLock.lock(RedisConstants.OKX_TICKER,10,3,1000);

            String str = HttpUtil.getOkx("/api/v5/market/tickers?instType=SPOT", null, accountMap);
            JSONObject json = JSONObject.parseObject(str);
            if (json == null || !json.getString("code").equals("0")) {
                return false;
            }
            List<OkxCoinTicker> dbTickerList = Lists.newArrayList();
            Date now = new Date();
            for (int i = 0; i <= 29; i++) {
                LambdaQueryWrapper<OkxCoinTicker> wrapper1 = new LambdaQueryWrapper();
                Date day =  DateUtil.addDate(now, i-29);
                wrapper1.ge(OkxCoinTicker::getCreateTime, DateUtil.getMinTime(day));
                wrapper1.le(OkxCoinTicker::getCreateTime, DateUtil.getMaxTime(day));
                List<OkxCoinTicker> tempList = this.tickerMapper.selectList(wrapper1);
                dbTickerList.addAll(tempList);
            }
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

                    Optional<OkxCoinTicker> dbticker = dbTickerList.stream().filter(obj -> obj.getCoin().equals(ticker.getCoin()))
                            .filter(obj -> obj.getCreateTime().getTime() >= DateUtil.getMinTime(now).getTime()).findFirst();
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
                    List<OkxCoinTicker> allTickerList = dbTickerList.stream().filter(obj -> obj.getCoin().equals(ticker.getCoin())).collect(Collectors.toList());
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
            syncCoinBusiness.updateCoin(jsonObjectList, tickerList, now, accountMap);
            redisLock.releaseLock(RedisConstants.OKX_TICKER);
        } catch (Exception e) {
            log.error("syncTicker error:", e);
            redisLock.releaseLock(RedisConstants.OKX_TICKER);
            throw new ServiceException("syncTicker error");
        }
        return true;
    }


}
