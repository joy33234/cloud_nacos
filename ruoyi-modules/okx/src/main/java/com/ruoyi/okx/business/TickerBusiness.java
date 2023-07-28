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
    private RedisService redisService;

    @Resource
    private SyncCoinBusiness syncCoinBusiness;

    @Resource
    private TradeBusiness tradeBusiness;

    /**
     * new
     * @return
     * @throws ServiceException
     */
    @Async
    public void syncTicker(JSONObject item, List<OkxCoinTicker> coinTickerList, List<OkxAccount> accountList,  Map<String, List<OkxSetting>> accountSettingMap,List<OkxBuyRecord> coinBuyRecords, boolean updateCoin,List<RiseDto> riseDtos) throws ServiceException{
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
            OkxCoin okxCoin = syncCoinBusiness.updateCoinV2(ticker, now, updateCoin);
            if (okxCoin == null) {
                return;
            }
            //交易
            tradeBusiness.tradeV2(accountList, okxCoin, ticker, now, accountSettingMap, coinBuyRecords, riseDtos);

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
            list.add(redisService.getCacheObject(key,OkxCoinTicker.class));
        }
        return list;
    }

    public OkxCoinTicker getTickerCache(String coin) {
        return redisService.getCacheObject(CacheConstants.OKX_TICKER_KEY + coin, OkxCoinTicker.class);
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
