package com.ruoyi.okx.business;


import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson.JSON;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.enums.Status;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.*;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.params.dto.TradeDto;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.Constant;
import com.ruoyi.okx.utils.DtoUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TradeBusiness {
    private static final Logger log = LoggerFactory.getLogger(TradeBusiness.class);

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private StrategyBusiness strategyBusiness;

    @Resource
    private SettingService settingService;

    @Resource
    private SellRecordBusiness sellRecordBusiness;

    @Resource
    private BuyStrategyBusiness buyStrategyBusiness;

    @Resource
    private AccountBusiness accountBusiness;

    @Resource
    private RedisService redisService;

    /**
     * 买卖交易
     * @param list
     * @param coin
     * @param map
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public void trade(List<TradeDto> list, OkxCoin coin, Map<String, String> map) throws ServiceException {
        try {
            Date now = new Date();
            Integer accountId = Integer.valueOf(map.get("id"));
            String accountName = map.get("accountName");
            for (TradeDto tradeDto:list) {
                if (tradeDto.getSide().equals(OkxSideEnum.BUY.getSide())) {
                    OkxBuyRecord buyRecord =
                            new OkxBuyRecord(null, tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
                                    tradeDto.getPx().multiply(tradeDto.getSz()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ZERO,
                                    OrderStatusEnum.PENDING.getStatus(), UUID.randomUUID().toString(), "", tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName,tradeDto.getMarketStatus(),tradeDto.getModeType());
                    if (!strategyBusiness.checkBuy(buyRecord, coin)){
                        return;
                    }
                    String okxOrderId = tradeOkx(tradeDto, now, map);
                    if (okxOrderId == null)
                        return;
                    buyRecord.setOkxOrderId(okxOrderId);
                    buyRecord.setCreateTime(now);
                    buyRecord.setUpdateTime(now);
                    buyRecordBusiness.save(buyRecord);
                } else {
                    OkxSellRecord sellRecord = new OkxSellRecord(null, tradeDto.getBuyRecordId(), tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
                            tradeDto.getPx().multiply(tradeDto.getSz()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP), BigDecimal.ZERO, OrderStatusEnum.PENDING.getStatus(),
                            UUID.randomUUID().toString(), "", tradeDto.getSellStrategyId(), tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName);
                    if (!strategyBusiness.checkSell(sellRecord, coin, tradeDto)) {
                        return;
                    }
                    String okxOrderId = tradeOkx(tradeDto, now, map);
                    if (okxOrderId == null) {
                        log.info("tradeDto：{}, okxOrderId：{}", JSON.toJSONString(tradeDto), okxOrderId);
                        return;
                    }
                    sellRecord.setOkxOrderId(okxOrderId);
                    sellRecord.setCreateTime(now);
                    sellRecord.setUpdateTime(now);
                    sellRecord.setStatus(OrderStatusEnum.PENDING.getStatus());
                    if (this.sellRecordBusiness.save(sellRecord)) {
                        buyRecordBusiness.updateBySell(sellRecord.getBuyRecordId(), OrderStatusEnum.SELLING.getStatus());
                    }
                }
            }
        } catch (Exception e) {
            log.error("okx trade error:{}", e);
            throw new ServiceException("okx trade error");
        }
    }

    public String tradeOkx(TradeDto tradeDto, Date now, Map<String, String> map) {
        return System.currentTimeMillis() + "";
//        Map<String, String> params = new HashMap<>(8);
//        params.put("instId", tradeDto.getInstId());
//        params.put("tdMode", "cash");
//        params.put("clOrdId", TokenUtil.getOkxOrderId(now));
//        params.put("side", tradeDto.getSide());
//        params.put("ordType", tradeDto.getOrdType());
//        params.put("sz", tradeDto.getSz().toString());
//        if (tradeDto.getOrdType().equals(OkxOrdTypeEnum.LIMIT.getValue())) {
//            params.put("px", tradeDto.getPx().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP) + "");
//        }
//        String str = HttpUtil.postOkx("/api/v5/trade/order", params, map);
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
    }


    @Async
    public void trade(List<OkxCoin> coins, List<OkxCoinTicker> tickers, Map<String, String> map) throws ServiceException {
        try {
            RiseDto riseDto = redisService.getCacheObject(RedisConstants.OKX_TICKER_MARKET);
            Integer accountId = Integer.valueOf(map.get("id"));

            //赋值用户订单类型和交易模式
            accountBusiness.listByAccountId(accountId).stream()
                    .filter(item -> item.getSettingKey().equals(OkxConstants.ORD_TYPE) || item.getSettingKey().equals(OkxConstants.MODE_TYPE))
                    .collect(Collectors.toList()).stream().forEach(obj -> map.put(obj.getSettingKey(), obj.getSettingValue()));

            List<OkxBuyRecord> buyRecords = buyRecordBusiness.findSuccessRecord(null, accountId, null,null);

            for (OkxCoinTicker ticker : tickers) {
                Optional<OkxCoin> OkxCoin = coins.stream().filter(obj -> obj.getCoin().equals(ticker.getCoin())).findFirst();
                if (!OkxCoin.isPresent()
                        || OkxCoin.get().getStandard().compareTo(BigDecimal.ZERO) <= 0
                        || OkxCoin.get().getStatus().intValue() == CoinStatusEnum.CLOSE.getStatus().intValue()) {
                    continue;
                }
                List<OkxBuyRecord> tempBuyRecords = buyRecords.stream().filter(item -> item.getCoin().equals(ticker.getCoin())).collect(Collectors.toList());
                //获取交易参数
                List<TradeDto> tradeDtoList = getTradeDto( OkxCoin.get(), ticker, map, riseDto, tempBuyRecords);
                if (CollectionUtils.isEmpty(tradeDtoList)) {
                    continue;
                }
                //交易
                trade(tradeDtoList, OkxCoin.get(), map);
            }

            //大盘交易
            if (map.get(OkxConstants.MODE_TYPE).equals(ModeTypeEnum.MARKET.getValue())
                    && (StringUtils.isNotEmpty(map.get("riseBuy")) || StringUtils.isNotEmpty(map.get("fallBuy")))) {
                if (StringUtils.isNotEmpty(map.get("riseBuy"))) {
                    riseDto.setRiseBought(true);
                }
                if (StringUtils.isNotEmpty(map.get("fallBuy"))) {
                    riseDto.setFallBought(true);
                }
                if (riseDto.getRiseBought() && riseDto.getFallBought()) {
                    riseDto.setStatus(Status.DISABLE.getCode());
                }
                redisService.setCacheObject(RedisConstants.OKX_TICKER_MARKET, riseDto);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServiceException("交易异常", 500);
        }
    }

    private List<TradeDto> getTradeDto( OkxCoin coin, OkxCoinTicker ticker, Map<String, String> map,RiseDto riseDto,List<OkxBuyRecord> buyRecords) {
        List<TradeDto> list = Lists.newArrayList();
        TradeDto tradeDto =  DtoUtils.transformBean(ticker, TradeDto.class);
        tradeDto.setUnit(coin.getUnit());
        tradeDto.setOrdType(map.get(OkxConstants.ORD_TYPE));
        String modeType = map.get(OkxConstants.MODE_TYPE);
        if (modeType.equals(ModeTypeEnum.GRID.getValue())) {
            tradeDto.setModeType(ModeTypeEnum.GRID.getValue());
            BigDecimal ins = ticker.getLast().subtract(coin.getStandard()).divide(coin.getStandard(), 8, RoundingMode.HALF_UP);
            //卖出
            if (ins.compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL))) >= 0) {
                buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.GRID.getValue())).forEach(item -> {
                    TradeDto temp =  DtoUtils.transformBean(tradeDto, TradeDto.class);
                    temp.setSz(item.getQuantity().subtract(item.getFee().abs()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                    temp.setTimes(item.getTimes());
                    temp.setBuyStrategyId(item.getStrategyId());
                    temp.setSide(OkxSideEnum.SELL.getSide());
                    temp.setBuyRecordId(item.getId());
                    temp.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
                    if (OkxOrdTypeEnum.LIMIT.getValue().equals(temp.getOrdType())) {
                        BigDecimal price = ticker.getLast().subtract(coin.getStandard().multiply(new BigDecimal(9.0E-4D)));
                        temp.setPx(price.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                    }
                    list.add(temp);
                });
                return list;
            }
            //买入
            if ((new BigDecimal(-0.011D)).compareTo(ins) >= 0) {
                OkxBuyStrategy buyStrategy = buyStrategyBusiness.list().stream()
                        .filter(strategy -> (strategy.getFallPercent().compareTo(ins.abs().setScale(2, RoundingMode.DOWN)) >= 0))
                        .sorted(Comparator.comparing(OkxBuyStrategy::getFallPercent)).findFirst().get();
                tradeDto.setSz(coin.getUnit().multiply(BigDecimal.valueOf(buyStrategy.getTimes())).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                tradeDto.setTimes(buyStrategy.getTimes());
                tradeDto.setBuyStrategyId(buyStrategy.getId());
                tradeDto.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
                //limit-设置价格
                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
                    BigDecimal buyPrice = ticker.getLast().add(ticker.getLast().multiply(new BigDecimal("9.0E-4")));
                    tradeDto.setPx(buyPrice.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                }
                tradeDto.setSide(OkxSideEnum.BUY.getSide());
                list.add(tradeDto);
            }
        } else if (modeType.equals(ModeTypeEnum.MARKET.getValue())) {
            if (riseDto == null) {
                return list;
            }
            tradeDto.setModeType(ModeTypeEnum.MARKET.getValue());

            List<OkxBuyRecord> tempBuyRecords = buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.MARKET.getValue())).collect(Collectors.toList());
            //卖出 - 当天以前的订单
            Date todayMinTime = DateUtil.getMinTime(new Date());
            List<OkxBuyRecord> beforeBuyRecords = tempBuyRecords.stream().filter(item -> todayMinTime.getTime() > item.getCreateTime().getTime()).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(beforeBuyRecords)) {
                beforeBuyRecords.stream().forEach(item -> {
                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
                    if (riseIns.compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.MARKET_LOW_SELL_PERCENT))) > 0) {
                        TradeDto temp =  DtoUtils.transformBean(tradeDto, TradeDto.class);
                        temp.setSz(item.getQuantity().subtract(item.getFee().abs()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                        temp.setTimes(item.getTimes());
                        temp.setBuyStrategyId(item.getStrategyId());
                        temp.setSide(OkxSideEnum.SELL.getSide());
                        temp.setBuyRecordId(item.getId());
                        temp.setMarketStatus(MarketStatusEnum.RISE.getStatus());
                        temp.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
                        if (OkxOrdTypeEnum.LIMIT.getValue().equals(temp.getOrdType())) {
                            BigDecimal price = ticker.getLast().subtract(coin.getStandard().multiply(new BigDecimal(9.0E-4D)));
                            temp.setPx(price.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                        }
                        list.add(temp);
                    }
                });
                return list;
            }

            //卖出 —— 大盘上涨时买入的
            if (CollectionUtils.isNotEmpty(tempBuyRecords) &&
                    (riseDto.getRisePercent().compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.MARKET_RISE_MAX_SELL_PERCENT))) > 0
                    || (riseDto.getHighest().compareTo(riseDto.getRisePercent()) > 0 && riseDto.getHighest().multiply(new BigDecimal(settingService.selectSettingByKey(OkxConstants.MARKET_RISE_SELL_PERCENT))).compareTo(riseDto.getRisePercent()) <= 0))) {
                tempBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.RISE.getStatus()).forEach(item -> {
                    TradeDto temp =  DtoUtils.transformBean(tradeDto, TradeDto.class);
                    temp.setSz(item.getQuantity().subtract(item.getFee().abs()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                    temp.setTimes(item.getTimes());
                    temp.setBuyStrategyId(item.getStrategyId());
                    temp.setSide(OkxSideEnum.SELL.getSide());
                    temp.setBuyRecordId(item.getId());
                    temp.setMarketStatus(MarketStatusEnum.RISE.getStatus());
                    temp.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
                    if (OkxOrdTypeEnum.LIMIT.getValue().equals(temp.getOrdType())) {
                        BigDecimal price = ticker.getLast().subtract(coin.getStandard().multiply(new BigDecimal(9.0E-4D)));
                        temp.setPx(price.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                    }
                    log.info("list-2:{}",JSON.toJSONString(list));

                    list.add(temp);
                });
                return list;
            }

            //卖出 —— 大盘下跌时买入的
            List<OkxBuyRecord> tempFallBuyRecords = tempBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.FALL.getStatus()).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(tempFallBuyRecords)) {
                tempFallBuyRecords.stream().forEach(item -> {
                    BigDecimal currentIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8,RoundingMode.DOWN);
                    if (currentIns.compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.MARKET_LOW_SELL_PERCENT))) > 0){
                        TradeDto temp =  DtoUtils.transformBean(tradeDto, TradeDto.class);
                        temp.setSz(item.getQuantity().subtract(item.getFee().abs()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                        temp.setTimes(item.getTimes());
                        temp.setBuyStrategyId(item.getStrategyId());
                        temp.setSide(OkxSideEnum.SELL.getSide());
                        temp.setBuyRecordId(item.getId());
                        temp.setMarketStatus(MarketStatusEnum.FALL.getStatus());
                        temp.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
                        if (OkxOrdTypeEnum.LIMIT.getValue().equals(temp.getOrdType())) {
                            BigDecimal price = ticker.getLast().subtract(coin.getStandard().multiply(new BigDecimal(9.0E-4D)));
                            temp.setPx(price.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                        }
                        list.add(temp);
                        log.info("list-3:{}",JSON.toJSONString(list));
                    }
                });
                return list;
            }
            if (riseDto.getStatus() == Status.DISABLE.getCode()) {
                return list;
            }
            Integer marketBuyTimes = Integer.valueOf(settingService.selectSettingByKey(OkxConstants.MARKET_BUY_TIMES));
            //大盘上涨-买入
            if (!riseDto.getRiseBought() && riseDto.getRisePercent().compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.MARKET_RISE_BUY_PERCENT))) > 0) {
                tradeDto.setTimes(marketBuyTimes);
                tradeDto.setSz(coin.getUnit().multiply(BigDecimal.valueOf(tradeDto.getTimes())).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                tradeDto.setPx(ticker.getLast().setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN));
                //设置价格
                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
                    BigDecimal buyPrice = ticker.getLast().add(ticker.getLast().multiply(new BigDecimal(9.0E-4D)));
                    tradeDto.setPx(buyPrice.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                }
                tradeDto.setMarketStatus(MarketStatusEnum.RISE.getStatus());
                tradeDto.setSide(OkxSideEnum.BUY.getSide());
                tradeDto.setBuyStrategyId(0);
                list.add(tradeDto);
                log.info("list-4:{}",JSON.toJSONString(list));
                map.put("riseBuy","true");
            }
            //大盘下跌-买入
            if (!riseDto.getFallBought() && riseDto.getLowPercent().compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.MARKET_LOW_BUY_PERCENT))) > 0) {
                tradeDto.setTimes(marketBuyTimes);
                tradeDto.setSz(coin.getUnit().multiply(BigDecimal.valueOf(tradeDto.getTimes())).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                tradeDto.setPx(ticker.getLast());
                //设置价格
                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
                    BigDecimal buyPrice = ticker.getLast().add(ticker.getLast().multiply(new BigDecimal(9.0E-4D)));
                    tradeDto.setPx(buyPrice.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
                }
                tradeDto.setMarketStatus(MarketStatusEnum.FALL.getStatus());
                tradeDto.setSide(OkxSideEnum.BUY.getSide());
                tradeDto.setBuyStrategyId(0);
                list.add(tradeDto);
                log.info("list-5:{}",JSON.toJSONString(list));
                map.put("fallBuy","true");
            }
        }
        return list;
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


}
