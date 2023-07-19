package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.*;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.*;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.params.dto.TradeDto;
import com.ruoyi.okx.utils.Constant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TradeBusiness {
    private static final Logger log = LoggerFactory.getLogger(TradeBusiness.class);

    @Resource
    private OkxTrandeBusiness okxTrandeBusiness;

    @Resource
    private RedisService redisService;

    @Autowired
    private RedisLock redisLock;

    @Value("${okx.newRedis}")
    public boolean newRedis;

    public String tradeOkx(TradeDto tradeDto, Date now, Map<String, String> map) {

        Map<String, String> params = new HashMap(8);
        params.put("instId", tradeDto.getInstId());
        params.put("tdMode", "cash");
        params.put("clOrdId", TokenUtil.getOkxOrderId(now));
        params.put("side", tradeDto.getSide());
        params.put("ordType", tradeDto.getOrdType());
        params.put("sz", tradeDto.getSz().toString());
        if (tradeDto.getSide().equalsIgnoreCase(OkxSideEnum.BUY.getSide())) {
            params.put("ordType", OkxOrdTypeEnum.LIMIT.getValue());
            params.put("px", tradeDto.getPx().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP) + "");

            //平均每个币种买入相同U数量
//            params.put("ordType", OkxOrdTypeEnum.MARKET.getValue());
        }
        String str = HttpUtil.postOkx("/api/v5/trade/order", params, map);
        JSONObject json = JSONObject.parseObject(str);
        if (json == null || !json.getString("code").equals("0")) {
            log.error("trade_param:{},str:{},px:{}", JSON.toJSONString(params), str, tradeDto.getPx().toString());
            return null;
        }
        JSONObject dataJSON = json.getJSONArray("data").getJSONObject(0);
        if (dataJSON == null || !dataJSON.getString("sCode").equals("0")) {
            log.error("tradeDto：{}", JSON.toJSONString(tradeDto));
            return null;
        }
        return dataJSON.getString("ordId");
    }


    //交易
    public void tradeV2(List<OkxAccount> accountList, OkxCoin okxCoin, OkxCoinTicker ticker, Date now,Map<String, List<OkxSetting>> accountSettingMap,List<OkxBuyRecord> coinBuyRecords,List<RiseDto> riseDtos) {
        String lockKey = "";
        for (OkxAccount okxAccount: accountList) {
            try {
                lockKey  = RedisConstants.OKX_TICKER_TRADE + "_" + okxAccount.getId() + "_" + ticker.getCoin();
                boolean lock = redisLock.lock(lockKey,600,1,1000);
                if (lock == false) {
                    log.error("tradeV2获取锁失败，交易取消 lockKey:{}",lockKey);
                    return;
                }
                List<OkxSetting> okxSettings  = accountSettingMap.get(Constant.OKX_ACCOUNT_SETTING + okxAccount.getId());

                //更新行情缓存
                Optional<RiseDto> riseDtoOptional = riseDtos.stream().filter(item -> item.getAccountId().intValue() == okxAccount.getId()).findFirst();
                if (riseDtoOptional.isPresent()) {
                    RiseDto riseDto = riseDtoOptional.get();
                    //赋值用户订单类型和交易模式
                    okxSettings.stream().forEach(obj -> {
                        if (obj.getSettingKey().equals(OkxConstants.ORD_TYPE)) {
                            riseDto.setOrderType(obj.getSettingValue());
                        }
                        if (obj.getSettingKey().equals(OkxConstants.MODE_TYPE)) {
                            riseDto.setModeType(obj.getSettingValue());
                        }
                    });
                    List<OkxBuyRecord> accountBuyRecords = coinBuyRecords.stream().filter(item -> item.getAccountId().intValue() == okxAccount.getId()).collect(Collectors.toList());
                    //okx交易
                    okxTrandeBusiness.okxTradeV2( okxCoin, ticker, okxSettings, accountBuyRecords, riseDto, now);
                }
            } catch (Exception e) {
                log.error("trade error",e);
                throw new ServiceException("交易异常", 500);
            } finally {
                redisLock.releaseLock(lockKey);
            }
        }
    }


    /**
     * 更新coin涨跌
     * @param now
     */
    public RiseDto refreshRiseCountV2(Integer riseCount, Integer fallCount, Date now, OkxAccount okxAccount,List<OkxSetting> settingList){
        String modeType = settingList.stream().filter(item -> item.getSettingKey().equals(OkxConstants.MODE_TYPE)).findFirst().get().getSettingValue();
        if (!modeType.equals(ModeTypeEnum.MARKET.getValue())) {
            return null;
        }
        String key = getCacheMarketKey(okxAccount.getId());

//        if ((now.getTime() - DateUtil.getMinTime(now).getTime() < 300000) || newRedis == true) {
//            RiseDto riseDto = new RiseDto();
//            riseDto.setAccountId(okxAccount.getId());
//            riseDto.setAccountName(okxAccount.getName());
//            riseDto.setApikey(okxAccount.getApikey());
//            riseDto.setSecretkey(okxAccount.getSecretkey());
//            riseDto.setPassword(okxAccount.getPassword());
//            redisService.setCacheObject(key, riseDto);
//
//            return null;
//        }

        RiseDto riseDto = redisService.getCacheObject(key);
        if (riseDto == null) {//redis异常 TODO
            return null;
        }
        Integer sum = fallCount + riseCount;

        BigDecimal risePercent = new BigDecimal(riseCount).divide(new BigDecimal(sum), 4,BigDecimal.ROUND_DOWN);
        BigDecimal lowPercent = BigDecimal.ONE.subtract(risePercent).setScale(Constant.OKX_BIG_DECIMAL);

        riseDto.setRiseCount(riseCount);
        riseDto.setRisePercent(risePercent);
        if (risePercent.compareTo(riseDto.getHighest()) > 0) {
            riseDto.setHighest(risePercent);
        }
        riseDto.setLowCount(sum - riseCount);
        riseDto.setLowPercent(lowPercent);
        if (lowPercent.compareTo(riseDto.getLowest()) > 0) {
            riseDto.setLowest(lowPercent);
        }
//        for (OkxCoin okxCoin:okxCoins) {
//            if (okxCoin.getCoin().equalsIgnoreCase("BTC")) {
//                riseDto.setBTCIns(okxCoin.getBtcIns());
//            }
//        }
        riseDto.setDateTime(DateUtil.getFormateDate(now,DateUtil.YYYY_MM_DD_HH_MM_SS));
        redisService.setCacheObject(key, riseDto);
        return riseDto;
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
        String key = getCacheMarketKey(okxAccount.getId());

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
        if (lowPercent.compareTo(riseDto.getLowest()) > 0) {
            riseDto.setLowest(lowPercent);
        }
        for (OkxCoin okxCoin:okxCoins) {
            if (okxCoin.getCoin().equalsIgnoreCase("BTC")) {
                riseDto.setBTCIns(okxCoin.getBtcIns());
            }
        }
        riseDto.setDateTime(DateUtil.getFormateDate(now,DateUtil.YYYY_MM_DD_HH_MM_SS));
        redisService.setCacheObject(key, riseDto);
        return riseDto;
    }


//    public String buyNewCoin(OkxCoinTicker ticker, Map<String, String> map) {
//        Date now = new Date();
//        BigDecimal sz = new BigDecimal(OkxConstants.NEW_COIN_MAX_USDT).divide(ticker.getLast(), 4, RoundingMode.DOWN);
//        Map<String, String> params = new HashMap<>(8);
//        params.put("instId", ticker.getInstId());
//        params.put("tdMode", "cash");
//        params.put("clOrdId", TokenUtil.getOkxOrderId(now));
//        params.put("side", "buy");
//        params.put("ordType", "market");
//        params.put("sz", sz.toString());
//        String str = HttpUtil.postOkx("/api/v5/trade/order", params, map);
//        JSONObject json = JSONObject.parseObject(str);
//        if (json == null || !json.getString("code").equals("0")) {
//            log.error("param:{},str:{},px:{}", JSON.toJSONString(params), str, ticker.getLast());
//            return null;
//        }
//        JSONObject dataJSON = json.getJSONArray("data").getJSONObject(0);
//        if (dataJSON == null || !dataJSON.getString("sCode").equals("0")) {
//            log.error("param:{},str:{},px:{}", JSON.toJSONString(params), str, ticker.getLast());
//            return null;
//        }
//        OkxBuyRecord buyRecord = new OkxBuyRecord(null, ticker.getCoin(), ticker.getInstId(), ticker.getLast(), sz, new BigDecimal(OkxConstants.NEW_COIN_MAX_USDT), BigDecimal.ZERO, BigDecimal.ZERO, OrderStatusEnum.NEW.getStatus(), UUID.randomUUID().toString(), dataJSON.getString("ordId"), Integer.valueOf(0), 0, Integer.valueOf(map.get("id")),map.get("accountName"));
//        buyRecord.setCreateTime(now);
//        buyRecord.setUpdateTime(now);
//        buyRecordBusiness.save(buyRecord);
//        return dataJSON.getString("ordId");
//    }

    public String getCacheMarketKey (Integer accountId) {
        return CacheConstants.OKX_MARKET  + ":" + accountId;
    }

}
