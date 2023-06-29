package com.ruoyi.okx.business;


import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
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
import com.ruoyi.common.core.enums.Status;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.*;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.*;
import com.ruoyi.okx.params.DO.OkxAccountBalanceDO;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.params.dto.TradeDto;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.Constant;
import com.ruoyi.okx.utils.DtoUtils;
import com.ruoyi.system.api.domain.SysDictData;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Comparator.comparing;

@Component
public class TradeBusiness {
    private static final Logger log = LoggerFactory.getLogger(TradeBusiness.class);

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private StrategyBusiness strategyBusiness;

    @Resource
    private SellRecordBusiness sellRecordBusiness;

    @Resource
    private BuyStrategyBusiness buyStrategyBusiness;

    @Resource
    private OkxTrandeBusiness okxTrandeBusiness;

    @Resource
    private RedisService redisService;

    @Autowired
    private RedisLock redisLock;

    @Resource
    private AccountBalanceBusiness balanceBusiness;

    @Resource
    private SettingService settingService;

    @Value("${okx.newRedis}")
    public boolean newRedis;
//    /**
//     * 买卖交易
//     * @param list
//     * @param coin
//     * @param map
//     * @throws Exception
//     */
//    @Transactional(rollbackFor = Exception.class)
//    @Async
//    public void trade(List<TradeDto> list, OkxCoin coin, Map<String, String> map, List<OkxSetting> okxSettings, RiseDto riseDto,BigDecimal tickerIns, BigDecimal totalBuyAmount) throws ServiceException {
//        try {
//            Long start = System.currentTimeMillis();
//            Date now = new Date();
//            Integer accountId = Integer.valueOf(map.get("id"));
//            String accountName = map.get("accountName");
//            for (TradeDto tradeDto:list) {
//                if (tradeDto.getSide().equals(OkxSideEnum.BUY.getSide())) {
//                    OkxBuyRecord buyRecord =
//                            new OkxBuyRecord(null, tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
//                                    tradeDto.getPx().multiply(tradeDto.getSz()), BigDecimal.ZERO, BigDecimal.ZERO,
//                                    OrderStatusEnum.PENDING.getStatus(), UUID.randomUUID().toString(), "", tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName,tradeDto.getMarketStatus(),tradeDto.getModeType(),null,null);
//                    if (!strategyBusiness.checkBuy(buyRecord, coin, okxSettings, totalBuyAmount)){
//                        return;
//                    }
//                    String okxOrderId = tradeOkx(tradeDto, now, map);
//                    if (okxOrderId == null) {
//                        return;
//                    }
//                    buyRecord.setRemark(riseDto == null ? "" : riseDto.getRisePercent() + "||" + tickerIns);
//                    buyRecord.setOkxOrderId(okxOrderId);
//                    buyRecord.setCreateTime(now);
//                    buyRecord.setUpdateTime(now);
//                    buyRecordBusiness.save(buyRecord);
//                } else {
//                    OkxSellRecord sellRecord = new OkxSellRecord(null, tradeDto.getBuyRecordId(), tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
//                            tradeDto.getPx().multiply(tradeDto.getSz()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ZERO, OrderStatusEnum.PENDING.getStatus(),
//                            UUID.randomUUID().toString(), "", tradeDto.getSellStrategyId(), tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName,null);
//                    if (!strategyBusiness.checkSell(sellRecord, coin, tradeDto)) {
//                        return;
//                    }
//                    String okxOrderId = tradeOkx(tradeDto, now, map);
//                    if (okxOrderId == null) {
//                        log.info("tradeDto：{}, okxOrderId：{}", JSON.toJSONString(tradeDto), okxOrderId);
//                        return;
//                    }
//                    sellRecord.setRemark(riseDto == null ? "" : riseDto.getRisePercent() + "||" + tickerIns);
//                    sellRecord.setOkxOrderId(okxOrderId);
//                    sellRecord.setCreateTime(now);
//                    sellRecord.setUpdateTime(now);
//                    sellRecord.setStatus(OrderStatusEnum.PENDING.getStatus());
//                    if (sellRecordBusiness.save(sellRecord)) {
//                        buyRecordBusiness.updateBySell(sellRecord.getBuyRecordId(), OrderStatusEnum.SELLING.getStatus());
//                    }
//                }
//            }
//            log.info("syncTicker - trade-okx coin:{} time:{}", coin.getCoin(), System.currentTimeMillis() - start);
//        } catch (Exception e) {
//            log.error("okx trade error ", e);
//            throw new ServiceException("okx trade error");
//        }
//    }


//    /**
//     * 买卖交易
//     * @param list
//     * @param coin
//     * @throws Exception
//     */
//    @Transactional(rollbackFor = Exception.class)
//    public void okxTradeV2(List<TradeDto> list, OkxCoin coin, List<OkxSetting> okxSettings, RiseDto riseDto,BigDecimal tickerIns, BigDecimal totalBuyAmount) throws ServiceException {
//        try {
//            Long start = System.currentTimeMillis();
//            Date now = new Date();
//            Integer accountId = riseDto.getAccountId();
//            String accountName = riseDto.getAccountName();
//            for (TradeDto tradeDto:list) {
//                if (tradeDto.getSide().equals(OkxSideEnum.BUY.getSide())) {
//                    OkxBuyRecord buyRecord =
//                            new OkxBuyRecord(null, tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
//                                    tradeDto.getPx().multiply(tradeDto.getSz()), BigDecimal.ZERO, BigDecimal.ZERO,
//                                    OrderStatusEnum.PENDING.getStatus(), UUID.randomUUID().toString(), "", tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName,tradeDto.getMarketStatus(),tradeDto.getModeType(),null,null);
//                    if (!strategyBusiness.checkBuy(buyRecord, coin, okxSettings, totalBuyAmount)){
//                        return;
//                    }
//                    String okxOrderId = okxRequest(tradeDto, now, riseDto);
//                    if (okxOrderId == null) {
//                        return;
//                    }
//                    buyRecord.setRemark(riseDto == null ? "" : riseDto.getRisePercent() + "||" + tickerIns);
//                    buyRecord.setOkxOrderId(okxOrderId);
//                    buyRecord.setCreateTime(now);
//                    buyRecord.setUpdateTime(now);
//                    buyRecordBusiness.save(buyRecord);
//                } else {
//                    OkxSellRecord sellRecord = new OkxSellRecord(null, tradeDto.getBuyRecordId(), tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
//                            tradeDto.getPx().multiply(tradeDto.getSz()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ZERO, OrderStatusEnum.PENDING.getStatus(),
//                            UUID.randomUUID().toString(), "", tradeDto.getSellStrategyId(), tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName,null);
//                    if (!strategyBusiness.checkSell(sellRecord, coin, tradeDto)) {
//                        return;
//                    }
//                    if (buyRecordBusiness.findOne(tradeDto.getBuyRecordId()).getStatus().intValue() != OrderStatusEnum.SUCCESS.getStatus()) {
//                        return;
//                    }
//                    String okxOrderId = okxRequest(tradeDto, now, riseDto);
//                    if (okxOrderId == null) {
//                        log.info("tradeDto：{}, okxOrderId：{}", JSON.toJSONString(tradeDto), okxOrderId);
//                        return;
//                    }
//                    sellRecord.setRemark(riseDto == null ? "" : riseDto.getRisePercent() + "||" + tickerIns);
//                    sellRecord.setOkxOrderId(okxOrderId);
//                    sellRecord.setCreateTime(now);
//                    sellRecord.setUpdateTime(now);
//                    sellRecord.setStatus(OrderStatusEnum.PENDING.getStatus());
//                    if (sellRecordBusiness.save(sellRecord)) {
//                        buyRecordBusiness.updateBySell(sellRecord.getBuyRecordId(), OrderStatusEnum.SELLING.getStatus());
//                    }
//                }
//            }
//            log.info("syncTicker - trade-okx coin:{} time:{}", coin.getCoin(), System.currentTimeMillis() - start);
//        } catch (Exception e) {
//            log.error("okx trade error ", e);
//            throw new ServiceException("okx trade error");
//        }
//    }

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


//    public String okxRequest(TradeDto tradeDto, Date now, RiseDto riseDto) {
//
//        Map<String, String> params = new HashMap(8);
//        params.put("instId", tradeDto.getInstId());
//        params.put("tdMode", "cash");
//        params.put("clOrdId", TokenUtil.getOkxOrderId(now));
//        params.put("side", tradeDto.getSide());
//        params.put("ordType", tradeDto.getOrdType());
//        params.put("sz", tradeDto.getSz().toString());
////        if (tradeDto.getSide().equalsIgnoreCase(OkxSideEnum.BUY.getSide())) {
//            params.put("ordType", OkxOrdTypeEnum.LIMIT.getValue());
//            params.put("px", tradeDto.getPx() + "");//setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP)
////        }
//        String str = HttpUtil.postOkxV2("/api/v5/trade/order", params, riseDto.getApikey(), riseDto.getPassword(), riseDto.getSecretkey());
//        JSONObject json = JSONObject.parseObject(str);
//        if (json == null || !json.getString("code").equals("0")) {
//            log.error("trade_param:{},str:{},px:{}", JSON.toJSONString(params), str, tradeDto.getPx().toString());
//            return null;
//        }
//        JSONObject dataJSON = json.getJSONArray("data").getJSONObject(0);
//        if (dataJSON == null || !dataJSON.getString("sCode").equals("0")) {
//            log.error("tradeDto：{}", JSON.toJSONString(tradeDto));
//            return null;
//        }
//        return dataJSON.getString("ordId");
//    }

//
//    @Async
//    public void trade(List<OkxCoin> coins, List<OkxCoinTicker> tickers,List<OkxSetting> okxSettings, Map<String, String> map) throws ServiceException {
//        Integer accountId = Integer.valueOf(map.get("id"));
//        String lockKey = RedisConstants.OKX_TICKER_TRADE + accountId;
//        try {
//            boolean lock = redisLock.lock(lockKey,30,3,5000);
//            if (lock == false) {
//                log.error("trade获取锁失败，交易取消");
//                return;
//            }
//            RiseDto riseDto = redisService.getCacheObject(this.getCacheMarketKey(accountId));
//
//            //赋值用户订单类型和交易模式
//            okxSettings.stream()
//                    .filter(item -> item.getSettingKey().equals(OkxConstants.ORD_TYPE) || item.getSettingKey().equals(OkxConstants.MODE_TYPE))
//                    .collect(Collectors.toList()).stream().forEach(obj -> map.put(obj.getSettingKey(), obj.getSettingValue()));
//            List<OkxBuyRecord> buyRecords = buyRecordBusiness.findSuccessRecord(null, accountId, null,null);
//            for (OkxCoinTicker ticker : tickers) {
//                Optional<OkxCoin> OkxCoin = coins.stream().filter(obj -> obj.getCoin().equals(ticker.getCoin())).findFirst();
//                if (!OkxCoin.isPresent() || OkxCoin.get().getStandard().compareTo(BigDecimal.ZERO) <= 0) {
//                    continue;
//                }
//                List<OkxBuyRecord> tempBuyRecords = buyRecords.stream().filter(item -> item.getCoin().equals(ticker.getCoin())).collect(Collectors.toList());
//                //获取交易参数
//                List<TradeDto> tradeDtoList = getTradeDto( OkxCoin.get(), ticker, map, riseDto, tempBuyRecords,okxSettings);
//                if (CollectionUtils.isEmpty(tradeDtoList)) {
//                    continue;
//                }
//                BigDecimal totalBuyAmount = tempBuyRecords.stream().map(OkxBuyRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
//                //交易
//                trade(tradeDtoList, OkxCoin.get(), map, okxSettings, riseDto,ticker.getIns(),totalBuyAmount);
//            }
//            //大盘交易
//            if (map.get(OkxConstants.MODE_TYPE).equals(ModeTypeEnum.MARKET.getValue())
//                    && (StringUtils.isNotEmpty(map.get("riseBuy")) || StringUtils.isNotEmpty(map.get("fallBuy")))) {
//                if (StringUtils.isNotEmpty(map.get("riseBuy"))) {
//                    riseDto.setRiseBought(true);
//                    riseDto.setBuyRisePercent(riseDto.getRisePercent());
//                    riseDto.setRiseBoughtTime(new Date());
//                }
//                if (StringUtils.isNotEmpty(map.get("fallBuy"))) {
//                    riseDto.setFallBought(true);
//                    riseDto.setBuyLowPercent(riseDto.getLowPercent());
//                    riseDto.setFallBoughtTime(new Date());
//                }
//                redisService.setCacheObject(this.getCacheMarketKey(accountId), riseDto);
//            }
//            redisLock.releaseLock(lockKey);
//        } catch (Exception e) {
//            redisLock.releaseLock(lockKey);
//            log.error("trade error",e);
//            throw new ServiceException("交易异常", 500);
//        }
//    }


    public void tradeV2(List<OkxAccount> accountList, List<OkxCoin> okxCoins, List<OkxCoinTicker> tickerList, Date now) {
        Long tradeV2Start = System.currentTimeMillis();
        //交易
        try {
            for (OkxAccount okxAccount: accountList) {
                if (okxAccount.getStatus().intValue() != Status.OK.getCode()) {
                    continue;
                }
                List<OkxSetting> okxSettings  = settingService.selectSettingByIds(DtoUtils.StringToLong(okxAccount.getSettingIds().split(",")));

                //更新行情缓存
                RiseDto riseDto = refreshRiseCountV2(okxCoins, now, okxAccount, okxSettings);
                if (riseDto != null) {
                    //赋值用户订单类型和交易模式
                    okxSettings.stream().forEach(obj -> {
                        if (obj.getSettingKey().equals(OkxConstants.ORD_TYPE)) {
                            riseDto.setOrderType(obj.getSettingValue());
                        }
                        if (obj.getSettingKey().equals(OkxConstants.MODE_TYPE)) {
                            riseDto.setModeType(obj.getSettingValue());
                        }
                    });

                    List<OkxBuyRecord> accountBuyRecords = buyRecordBusiness.findSuccessRecord(null, okxAccount.getId(), null,null);
                    //按coin排序
                    tickerList = tickerList.stream().sorted(Comparator.comparing(OkxCoinTicker::getCoin)).collect(Collectors.toList());
                    for (OkxCoinTicker ticker : tickerList) {
                        okxTrandeBusiness.okxTradeV2( okxCoins,  ticker, okxSettings,  accountBuyRecords, riseDto, now);
                    }
                }
            }
        } catch (Exception e) {
            log.error("trade error",e);
            throw new ServiceException("交易异常", 500);
        }
        log.info("tradeV2 time:{}",System.currentTimeMillis() - tradeV2Start);
    }
//
//    public void tradeV2(List<OkxCoin> coins, List<OkxCoinTicker> tickers,List<OkxSetting> okxSettings,RiseDto riseDto) throws ServiceException {
//        Long tradeV2Start = System.currentTimeMillis();
//        Integer accountId = riseDto.getAccountId();
//        Date now = new Date();
//        try {
//            //赋值用户订单类型和交易模式
//            okxSettings.stream().forEach(obj -> {
//                if (obj.getSettingKey().equals(OkxConstants.ORD_TYPE)) {
//                    riseDto.setOrderType(obj.getSettingValue());
//                }
//                if (obj.getSettingKey().equals(OkxConstants.MODE_TYPE)) {
//                    riseDto.setModeType(obj.getSettingValue());
//                }
//            });
//
////            boolean riseBuy = false,fallbuy = false;
//            List<OkxBuyRecord> accountBuyRecords = buyRecordBusiness.findSuccessRecord(null, accountId, null,null);
//            //按coin排序
//            tickers = tickers.stream().sorted(Comparator.comparing(OkxCoinTicker::getCoin)).collect(Collectors.toList());
//            for (OkxCoinTicker ticker : tickers) {
//                okxTrandeBusiness.okxTradeV2( coins,  ticker, okxSettings,  accountBuyRecords, riseDto, ticker.getIns(), now);
//            }
//            //大盘交易
////            if (riseDto.getModeType().equals(ModeTypeEnum.MARKET.getValue())
////                    && (fallbuy || riseBuy)) {
////                if (riseBuy) {
////                    riseDto.setRiseBought(true);
////                    riseDto.setBuyRisePercent(riseDto.getRisePercent());
////                    riseDto.setRiseBoughtTime(new Date());
////                }
////                if (fallbuy) {
////                    riseDto.setFallBought(true);
////                    riseDto.setBuyLowPercent(riseDto.getLowPercent());
////                    riseDto.setFallBoughtTime(new Date());
////                }
////                redisService.setCacheObject(this.getCacheMarketKey(accountId), riseDto);
////            }
//        } catch (Exception e) {
//            log.error("trade error",e);
//            throw new ServiceException("交易异常", 500);
//        }
//        log.info("tradeV2 time:{}",System.currentTimeMillis() - tradeV2Start);
//    }


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
//
//    private List<TradeDto> getTradeDto( OkxCoin coin, OkxCoinTicker ticker, Map<String, String> map,RiseDto riseDto,List<OkxBuyRecord> buyRecords,List<OkxSetting> okxSettings) {
//        List<TradeDto> list = Lists.newArrayList();
//        TradeDto tradeDto =  DtoUtils.transformBean(ticker, TradeDto.class);
//        tradeDto.setUnit(coin.getUnit());
//        tradeDto.setOrdType(map.get(OkxConstants.ORD_TYPE));
//        String modeType = map.get(OkxConstants.MODE_TYPE);
//        //买入数量
//        BigDecimal buyUsdtAmout = new BigDecimal(okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.BUY_USDT_AMOUNT)).findFirst().get().getSettingValue());
//        BigDecimal buyPrice = ticker.getLast().add(ticker.getLast().multiply(new BigDecimal(9.0E-4D)));
//        BigDecimal buySz = buyUsdtAmout.divide(buyPrice,Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN);
//
//        BigDecimal ins = ticker.getLast().subtract(coin.getStandard()).divide(coin.getStandard(), 8, RoundingMode.HALF_UP);
//
//        if (modeType.equals(ModeTypeEnum.GRID.getValue())) {
//            //卖出
//            tradeDto.setModeType(ModeTypeEnum.GRID.getValue());
//
//            BigDecimal gridMinSellPercent = new BigDecimal(okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL)).findFirst().get().getSettingValue());
//            buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.GRID.getValue()))
//                    .filter(item -> (ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(), Constant.OKX_BIG_DECIMAL,RoundingMode.DOWN)).compareTo(gridMinSellPercent) > 0)
//                    .forEach(item -> {
//                TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                temp.setMarketStatus(MarketStatusEnum.MAX_RISE.getStatus());
//                list.add(temp);
//            });
//
//
//            //TODO - test
////            if (ins.compareTo(new BigDecimal(okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL)).findFirst().get().getSettingValue())) >= 0) {
////                buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.GRID.getValue())).forEach(item -> {
////                    TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
////                    temp.setMarketStatus(MarketStatusEnum.MAX_RISE.getStatus());
////                    list.add(temp);
////                });
////                return list;
////            }
//
//
//            //买入
//            if ((new BigDecimal(-0.011D)).compareTo(ins) >= 0
//                && coin.getStatus() == CoinStatusEnum.OPEN.getStatus()) {
//                OkxBuyStrategy buyStrategy = buyStrategyBusiness.list().stream()
//                        .filter(strategy -> (strategy.getFallPercent().compareTo(ins.abs().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN)) >= 0))
//                        .sorted(comparing(OkxBuyStrategy::getFallPercent)).findFirst().get();
//                tradeDto.setSz(buySz);
//                tradeDto.setTimes(buyStrategy.getTimes());
//                tradeDto.setBuyStrategyId(buyStrategy.getId());
//                tradeDto.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
//                //limit-设置价格
//                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
//                    tradeDto.setPx(buyPrice);
//                }
//                tradeDto.setSide(OkxSideEnum.BUY.getSide());
//                list.add(tradeDto);
//            }
//        } else if (modeType.equals(ModeTypeEnum.MARKET.getValue())) {
//            if (riseDto == null) {
//                return list;
//            }
//            tradeDto.setModeType(ModeTypeEnum.MARKET.getValue());
//
//            List<OkxBuyRecord> marketBuyRecords = buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.MARKET.getValue())).collect(Collectors.toList());
//            //卖出 - 当天以前的订单
//            Date todayMinTime = DateUtil.getMinTime(new Date());
//            List<OkxBuyRecord> beforeBuyRecords = marketBuyRecords.stream().filter(item -> todayMinTime.getTime() > item.getCreateTime().getTime()).collect(Collectors.toList());
//            if (CollectionUtils.isNotEmpty(beforeBuyRecords)) {
//                beforeBuyRecords.stream().forEach(item -> {
//                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
//                    if (riseIns.compareTo(new BigDecimal(okxSettings.stream().filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_LOW_SELL_PERCENT)).findFirst().get().getSettingValue())) > 0) {
//                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                        temp.setMarketStatus(MarketStatusEnum.RISE.getStatus());
//                        list.add(temp);
//                    }
//                });
//            }
//
//            //卖出 - 当天上涨超过设置最大值
//            if (CollectionUtils.isNotEmpty(marketBuyRecords) && riseDto.getRiseBought() == true
//                    && riseDto.getRisePercent().compareTo(new BigDecimal(okxSettings.stream()
//                            .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_RISE_MAX_SELL_PERCENT)).findFirst().get().getSettingValue())) > 0) {
//                marketBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.RISE.getStatus()).forEach(item -> {
//                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
//                    if (riseIns.compareTo(new BigDecimal(0.02)) >= 0) {
//                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                        temp.setMarketStatus(MarketStatusEnum.MAX_RISE.getStatus());
//                        list.add(temp);
//                    }
//                });
//                riseDto.setSellPercent(riseDto.getRisePercent());
//            }
//            //卖出 - 当天上涨最大值百分比
//            if (CollectionUtils.isNotEmpty(marketBuyRecords) && riseDto.getRiseBought() == true
//                && riseDto.getHighest().multiply(new BigDecimal(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_RISE_SELL_PERCENT)).findFirst().get().getSettingValue())).compareTo(riseDto.getRisePercent()) >= 0)
//            {
//                marketBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.RISE.getStatus()).forEach(item -> {
//                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
//                    if (riseIns.compareTo(new BigDecimal(0.02)) >= 0) {
//                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                        temp.setMarketStatus(MarketStatusEnum.RISE.getStatus());
//                        list.add(temp);
//                        riseDto.setSellPercent(riseDto.getRisePercent());
//                    }
//                });
//            }
//
//            //卖出 —— 大盘下跌时买入的
//            List<OkxBuyRecord> tempFallBuyRecords = marketBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.FALL.getStatus()).collect(Collectors.toList());
//            if (CollectionUtils.isNotEmpty(tempFallBuyRecords)) {
//                tempFallBuyRecords.stream().forEach(item -> {
//                    BigDecimal currentIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8,RoundingMode.DOWN);
//                    if (currentIns.compareTo(new BigDecimal(okxSettings.stream()
//                                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_LOW_SELL_PERCENT)).findFirst().get().getSettingValue())) > 0){
//                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                        temp.setMarketStatus(MarketStatusEnum.FALL.getStatus());
//                        list.add(temp);
//                    }
//                });
//            }
//
//            if (riseDto.getStatus() == Status.DISABLE.getCode()) {
//                return list;
//            }
//
//            Integer marketBuyTimes = Integer.valueOf(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_BUY_TIMES)).findFirst().get().getSettingValue());
//            BigDecimal marketRiseBuyPercent = new BigDecimal(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_RISE_BUY_PERCENT)).findFirst().get().getSettingValue());
//            //买入- 大盘上涨
//            if (!riseDto.getRiseBought() && coin.getStatus() == CoinStatusEnum.OPEN.getStatus()
//                    && riseDto.getRisePercent().compareTo(marketRiseBuyPercent) > 0
//                    && marketRiseBuyPercent.add(marketRiseBuyPercent.multiply(new BigDecimal(0.1))).compareTo(riseDto.getRisePercent()) > 0
//                    && riseDto.getBTCIns().compareTo(new BigDecimal(okxSettings.stream()
//                            .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_BTC_RISE_INS)).findFirst().get().getSettingValue())) > 0 ) {
//                tradeDto.setTimes(marketBuyTimes);
//                tradeDto.setSz(buySz);
//                tradeDto.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
//                //设置价格
//                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
//                    tradeDto.setPx(buyPrice);
//                }
//                tradeDto.setMarketStatus(MarketStatusEnum.RISE.getStatus());
//                tradeDto.setSide(OkxSideEnum.BUY.getSide());
//                tradeDto.setBuyStrategyId(0);
//                list.add(tradeDto);
//                map.put("riseBuy","true");
//            }
//
//            //买入- 大盘下跌
//            if (!riseDto.getFallBought() && coin.getStatus() == CoinStatusEnum.OPEN.getStatus() && ins.compareTo(BigDecimal.ZERO) <= 0 //当前价格小于等于标准值
//                    && riseDto.getLowPercent().compareTo(new BigDecimal(okxSettings.stream()
//                            .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_LOW_BUY_PERCENT)).findFirst().get().getSettingValue())) > 0
//                    && new BigDecimal(okxSettings.stream()
//                            .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_BTC_FALL_INS)).findFirst().get().getSettingValue()).compareTo(riseDto.getBTCIns()) > 0 ) {
//                tradeDto.setTimes(marketBuyTimes);
//                tradeDto.setSz(buySz);
//                tradeDto.setPx(ticker.getLast());
//                //设置价格
//                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
//                    tradeDto.setPx(buyPrice);
//                }
//                tradeDto.setMarketStatus(MarketStatusEnum.FALL.getStatus());
//                tradeDto.setSide(OkxSideEnum.BUY.getSide());
//                tradeDto.setBuyStrategyId(0);
//                list.add(tradeDto);
//                map.put("fallBuy","true");
//            }
//        }
//        return list.size() <= 1 ? list : list.stream().collect(Collectors.collectingAndThen(
//                Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(TradeDto::getBuyRecordId))), ArrayList::new ));
//    }
//
//    public List<TradeDto> getTradeDtoV2( OkxCoin coin, OkxCoinTicker ticker, RiseDto riseDto,List<OkxBuyRecord> buyRecords,List<OkxSetting> okxSettings) {
//        List<TradeDto> list = Lists.newArrayList();
//        TradeDto tradeDto =  DtoUtils.transformBean(ticker, TradeDto.class);
//        tradeDto.setUnit(coin.getUnit());
//        tradeDto.setOrdType(riseDto.getOrderType());
//        String modeType = riseDto.getModeType();
//        //买入数量
//        BigDecimal buyUsdtAmout = new BigDecimal(okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.BUY_USDT_AMOUNT)).findFirst().get().getSettingValue());
//        BigDecimal buyPrice = ticker.getLast().add(ticker.getLast().multiply(new BigDecimal(9.0E-4D)));
//        BigDecimal buySz = buyUsdtAmout.divide(buyPrice,Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN);
//        if(buySz.compareTo(BigDecimal.ONE) > 0) {
//            buySz.setScale(4,RoundingMode.DOWN);
//        }
//
//        BigDecimal ins = ticker.getLast().subtract(coin.getStandard()).divide(coin.getStandard(), 8, RoundingMode.HALF_UP);
//
//        if (modeType.equals(ModeTypeEnum.GRID.getValue())) {
//            //卖出
//            tradeDto.setModeType(ModeTypeEnum.GRID.getValue());
//
//            BigDecimal gridMinSellPercent = new BigDecimal(okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL)).findFirst().get().getSettingValue());
//            buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.GRID.getValue()))
//                    .filter(item -> (ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(), Constant.OKX_BIG_DECIMAL,RoundingMode.DOWN)).compareTo(gridMinSellPercent) > 0)
//                    .forEach(item -> {
//                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                        temp.setMarketStatus(MarketStatusEnum.MAX_RISE.getStatus());
//                        list.add(temp);
//                    });
//
//
//            //TODO - test
////            if (ins.compareTo(new BigDecimal(okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL)).findFirst().get().getSettingValue())) >= 0) {
////                buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.GRID.getValue())).forEach(item -> {
////                    TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
////                    temp.setMarketStatus(MarketStatusEnum.MAX_RISE.getStatus());
////                    list.add(temp);
////                });
////                return list;
////            }
//
//
//            //买入
//            if ((new BigDecimal(-0.011D)).compareTo(ins) >= 0
//                    && coin.getStatus() == CoinStatusEnum.OPEN.getStatus()) {
//                OkxBuyStrategy buyStrategy = buyStrategyBusiness.list().stream()
//                        .filter(strategy -> (strategy.getFallPercent().compareTo(ins.abs().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN)) >= 0))
//                        .sorted(comparing(OkxBuyStrategy::getFallPercent)).findFirst().get();
//                tradeDto.setSz(buySz);
//                tradeDto.setTimes(buyStrategy.getTimes());
//                tradeDto.setBuyStrategyId(buyStrategy.getId());
//                tradeDto.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
//                //limit-设置价格
//                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
//                    tradeDto.setPx(buyPrice);
//                }
//                tradeDto.setSide(OkxSideEnum.BUY.getSide());
//                list.add(tradeDto);
//            }
//        } else if (modeType.equals(ModeTypeEnum.MARKET.getValue())) {
//            if (riseDto == null) {
//                return list;
//            }
//            tradeDto.setModeType(ModeTypeEnum.MARKET.getValue());
//
//            List<OkxBuyRecord> marketBuyRecords = buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.MARKET.getValue())).collect(Collectors.toList());
//            //卖出 - 当天以前的订单
//            Date todayMinTime = DateUtil.getMinTime(new Date());
//            List<OkxBuyRecord> beforeBuyRecords = marketBuyRecords.stream().filter(item -> todayMinTime.getTime() > item.getCreateTime().getTime()).collect(Collectors.toList());
//            if (CollectionUtils.isNotEmpty(beforeBuyRecords)) {
//                beforeBuyRecords.stream().forEach(item -> {
//                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
//                    if (riseIns.compareTo(new BigDecimal(okxSettings.stream().filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_LOW_SELL_PERCENT)).findFirst().get().getSettingValue())) > 0) {
//                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                        temp.setMarketStatus(MarketStatusEnum.RISE.getStatus());
//                        list.add(temp);
//                    }
//                });
//            }
//
//            //卖出 - 当天上涨超过设置最大值
//            if (CollectionUtils.isNotEmpty(marketBuyRecords) && riseDto.getRiseBought() == true
//                    && riseDto.getRisePercent().compareTo(new BigDecimal(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_RISE_MAX_SELL_PERCENT)).findFirst().get().getSettingValue())) > 0) {
//                marketBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.RISE.getStatus()).forEach(item -> {
//                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
//                    if (riseIns.compareTo(new BigDecimal(0.02)) >= 0) {
//                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                        temp.setMarketStatus(MarketStatusEnum.MAX_RISE.getStatus());
//                        list.add(temp);
//                    }
//                });
//                riseDto.setSellPercent(riseDto.getRisePercent());
//            }
//            //卖出 - 当天上涨最大值百分比
//            if (CollectionUtils.isNotEmpty(marketBuyRecords) && riseDto.getRiseBought() == true
//                    && riseDto.getHighest().multiply(new BigDecimal(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_RISE_SELL_PERCENT)).findFirst().get().getSettingValue())).compareTo(riseDto.getRisePercent()) >= 0)
//            {
//                marketBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.RISE.getStatus()).forEach(item -> {
//                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
//                    if (riseIns.compareTo(new BigDecimal(0.02)) >= 0) {
//                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                        temp.setMarketStatus(MarketStatusEnum.RISE.getStatus());
//                        list.add(temp);
//                        riseDto.setSellPercent(riseDto.getRisePercent());
//                    }
//                });
//            }
//
//            //卖出 —— 大盘下跌时买入的
//            List<OkxBuyRecord> tempFallBuyRecords = marketBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.FALL.getStatus()).collect(Collectors.toList());
//            if (CollectionUtils.isNotEmpty(tempFallBuyRecords)) {
//                tempFallBuyRecords.stream().forEach(item -> {
//                    BigDecimal currentIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8,RoundingMode.DOWN);
//                    if (currentIns.compareTo(new BigDecimal(okxSettings.stream()
//                            .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_LOW_SELL_PERCENT)).findFirst().get().getSettingValue())) > 0){
//                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                        temp.setMarketStatus(MarketStatusEnum.FALL.getStatus());
//                        list.add(temp);
//                    }
//                });
//            }
//
//            if (riseDto.getStatus() == Status.DISABLE.getCode()) {
//                return list;
//            }
//
//            Integer marketBuyTimes = Integer.valueOf(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_BUY_TIMES)).findFirst().get().getSettingValue());
//            BigDecimal marketRiseBuyPercent = new BigDecimal(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_RISE_BUY_PERCENT)).findFirst().get().getSettingValue());
//            //买入- 大盘上涨
//            if (!riseDto.getRiseBought() && coin.getStatus() == CoinStatusEnum.OPEN.getStatus()
//                    && riseDto.getRisePercent().compareTo(marketRiseBuyPercent) > 0
//                    && marketRiseBuyPercent.add(marketRiseBuyPercent.multiply(new BigDecimal(0.1))).compareTo(riseDto.getRisePercent()) > 0
//                    && riseDto.getBTCIns().compareTo(new BigDecimal(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_BTC_RISE_INS)).findFirst().get().getSettingValue())) > 0 ) {
//                tradeDto.setTimes(marketBuyTimes);
//                tradeDto.setSz(buySz);
//                tradeDto.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
//                //设置价格
//                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
//                    tradeDto.setPx(buyPrice);
//                }
//                tradeDto.setMarketStatus(MarketStatusEnum.RISE.getStatus());
//                tradeDto.setSide(OkxSideEnum.BUY.getSide());
//                tradeDto.setBuyStrategyId(0);
//                list.add(tradeDto);
//            }
//
//            //买入- 大盘下跌
//            if (!riseDto.getFallBought() && coin.getStatus() == CoinStatusEnum.OPEN.getStatus() && ins.compareTo(BigDecimal.ZERO) <= 0 //当前价格小于等于标准值
//                    && riseDto.getLowPercent().compareTo(new BigDecimal(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_LOW_BUY_PERCENT)).findFirst().get().getSettingValue())) > 0
//                    && new BigDecimal(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_BTC_FALL_INS)).findFirst().get().getSettingValue()).compareTo(riseDto.getBTCIns()) > 0 ) {
//                tradeDto.setTimes(marketBuyTimes);
//                tradeDto.setSz(buySz);
//                tradeDto.setPx(ticker.getLast());
//                //设置价格
//                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
//                    tradeDto.setPx(buyPrice);
//                }
//                tradeDto.setMarketStatus(MarketStatusEnum.FALL.getStatus());
//                tradeDto.setSide(OkxSideEnum.BUY.getSide());
//                tradeDto.setBuyStrategyId(0);
//                list.add(tradeDto);
//            }
//        }
//        return list.size() <= 1 ? list : list.stream().collect(Collectors.collectingAndThen(
//                Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(TradeDto::getBuyRecordId))), ArrayList::new ));
//    }
//
//    private TradeDto getSellDto (TradeDto tradeDto,OkxCoinTicker ticker,OkxCoin coin,OkxBuyRecord item) {
//        TradeDto temp =  DtoUtils.transformBean(tradeDto, TradeDto.class);
//        temp.setSz(item.getQuantity().subtract(item.getFee().abs()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
//        temp.setTimes(item.getTimes());
//        temp.setBuyStrategyId(item.getStrategyId());
//        temp.setSide(OkxSideEnum.SELL.getSide());
//        temp.setBuyRecordId(item.getId());
//        temp.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
//        if (OkxOrdTypeEnum.LIMIT.getValue().equals(temp.getOrdType())) {
//            BigDecimal price = ticker.getLast().subtract(coin.getStandard().multiply(new BigDecimal(9.0E-4D)));
//            temp.setPx(price.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
//        }
//        return temp;
//    }



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
