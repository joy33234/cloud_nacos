package com.ruoyi.okx.business;


import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.enums.Status;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.core.utils.TokenUtil;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.*;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.params.dto.TradeDto;
import com.ruoyi.okx.utils.Constant;
import com.ruoyi.okx.utils.DtoUtils;
import com.ruoyi.okx.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

@Component
@Slf4j
public class OkxTrandeBusiness {

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private StrategyBusiness strategyBusiness;

    @Resource
    private SellRecordBusiness sellRecordBusiness;

    @Autowired
    private RedisLock redisLock;

    @Resource
    private CoinBusiness coinBusiness;

    @Resource
    private BuyStrategyBusiness buyStrategyBusiness;

    @Resource
    private AccountBalanceBusiness balanceBusiness;

    /**
     * 买卖交易
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public void okxTradeV2(OkxCoin coin,  OkxCoinTicker ticker, List<OkxSetting> okxSettings, List<OkxBuyRecord> buyRecords, RiseDto riseDto, Date now) throws ServiceException {
        try {
            if (ticker.getCoin().equalsIgnoreCase("aave") || ticker.getCoin().equalsIgnoreCase("zyro")) {
                log.info("coin:{} accountId:{},buyRecordsSize:{}",ticker.getCoin(), riseDto.getAccountId(), buyRecords.size());
            }
            Integer accountId = riseDto.getAccountId();
            String accountName = riseDto.getAccountName();
            String lockKey  = RedisConstants.OKX_TICKER_TRADE + "_" + accountId + "_" + ticker.getCoin();
            boolean lock = redisLock.lock(lockKey,30,1,1000);
            if (lock == false) {
                log.error("tradeV2获取锁失败，交易取消 lockKey:{}",lockKey);
                return;
            }

            //获取交易参数
            List<TradeDto> tradeDtoList = getTradeDtoV2(coin, ticker,  riseDto, buyRecords, okxSettings);
            if (CollectionUtils.isNotEmpty(tradeDtoList)) {
                OkxAccountBalance accountBalance = balanceBusiness.getAccountBalance(coin.getCoin(), accountId);
                if (ObjectUtils.isNotEmpty(accountBalance)) {
                    coin.setBalance(accountBalance.getBalance());
                }

                BigDecimal coinTotalBuyAmount = buyRecords.stream().map(OkxBuyRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                for (TradeDto tradeDto:tradeDtoList) {
                    if (tradeDto.getSide().equals(OkxSideEnum.BUY.getSide())) {
                        OkxBuyRecord buyRecord =
                                new OkxBuyRecord(null, tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
                                        tradeDto.getPx().multiply(tradeDto.getSz()), BigDecimal.ZERO, BigDecimal.ZERO,
                                        OrderStatusEnum.PENDING.getStatus(), UUID.randomUUID().toString(), "", tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName,tradeDto.getMarketStatus(),tradeDto.getModeType(),null,null);
                        if (!strategyBusiness.checkBuy(buyRecord, coin, okxSettings, coinTotalBuyAmount)){
                            continue;
                        }
                        String okxOrderId = okxRequest(tradeDto, now, riseDto);
                        if (okxOrderId == null) {
                            continue;
                        }
                        buyRecord.setRemark(riseDto == null ? "" : riseDto.getRisePercent() + "||" + ticker.getIns());
                        buyRecord.setOkxOrderId(okxOrderId);
                        buyRecord.setCreateTime(now);
                        buyRecord.setUpdateTime(now);
                        buyRecordBusiness.save(buyRecord);
                        coinBusiness.markBuy(coin.getCoin(), accountId);
                    } else {
                        OkxSellRecord sellRecord = new OkxSellRecord(null, tradeDto.getBuyRecordId(), tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
                                tradeDto.getPx().multiply(tradeDto.getSz()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ZERO, OrderStatusEnum.PENDING.getStatus(),
                                UUID.randomUUID().toString(), "", tradeDto.getSellStrategyId(), tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName,null);
                        if (!strategyBusiness.checkSell(sellRecord, coin, tradeDto)) {
                            continue;
                        }
                        if (buyRecordBusiness.findOne(tradeDto.getBuyRecordId()).getStatus().intValue() != OrderStatusEnum.SUCCESS.getStatus()) {
                            continue;
                        }
                        String okxOrderId = okxRequest(tradeDto, now, riseDto);
                        if (okxOrderId == null) {
                            log.info("tradeDto：{}, okxOrderId：{}", JSON.toJSONString(tradeDto), okxOrderId);
                            continue;
                        }
                        sellRecord.setRemark(riseDto == null ? "" : riseDto.getRisePercent() + "||" + ticker.getIns());
                        sellRecord.setOkxOrderId(okxOrderId);
                        sellRecord.setCreateTime(now);
                        sellRecord.setUpdateTime(now);
                        sellRecord.setStatus(OrderStatusEnum.PENDING.getStatus());
                        if (sellRecordBusiness.save(sellRecord)) {
                            buyRecordBusiness.updateBySell(sellRecord.getBuyRecordId(), OrderStatusEnum.SELLING.getStatus());
                        }
                    }
                }
            }
            redisLock.releaseLock(lockKey);
        } catch (Exception e) {
            log.error("trade error",e);
            throw new ServiceException("交易异常", 500);
        }
    }



    /**
     * 买卖交易
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    @Async
    public void okxTradeV2(List<OkxCoin> coins,  OkxCoinTicker ticker, List<OkxSetting> okxSettings, List<OkxBuyRecord> buyRecords, RiseDto riseDto, Date now) throws ServiceException {
        try {
            if (ticker.getCoin().equalsIgnoreCase("aave") || ticker.getCoin().equalsIgnoreCase("zyro")) {
                log.info("coin:{} accountId:{},buyRecords size:{}",ticker.getCoin(), riseDto.getAccountId(), buyRecords.size());
            }
            OkxCoin coin = null;
            Integer accountId = riseDto.getAccountId();
            String accountName = riseDto.getAccountName();
            String lockKey  = RedisConstants.OKX_TICKER_TRADE + "_" + accountId + "_" + ticker.getCoin();
            boolean lock = redisLock.lock(lockKey,30,1,1000);
            if (lock == false) {
                log.error("tradeV2获取锁失败，交易取消 lockKey:{}",lockKey);
                return;
            }

            Optional<OkxCoin> OkxCoin = coins.stream().filter(obj -> obj.getCoin().equals(ticker.getCoin())).findFirst();
            if (OkxCoin.isPresent() == false) {
                redisLock.releaseLock(lockKey);
                return;
            } else {
                coin = OkxCoin.get();
            }

            List<OkxBuyRecord> coinBuyRecords = buyRecords.stream().filter(item -> item.getCoin().equals(ticker.getCoin())).collect(Collectors.toList());
            //获取交易参数
            Long start = System.currentTimeMillis();
            List<TradeDto> tradeDtoList = getTradeDtoV2( OkxCoin.get(), ticker,  riseDto, coinBuyRecords,okxSettings);

            if (CollectionUtils.isNotEmpty(tradeDtoList)) {
                OkxAccountBalance accountBalance = balanceBusiness.getAccountBalance(coin.getCoin(), accountId);
                if (ObjectUtils.isNotEmpty(accountBalance)) {
                    coin.setBalance(accountBalance.getBalance());
                }

                BigDecimal coinTotalBuyAmount = coinBuyRecords.stream().map(OkxBuyRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                for (TradeDto tradeDto:tradeDtoList) {
                    if (tradeDto.getSide().equals(OkxSideEnum.BUY.getSide())) {
                        OkxBuyRecord buyRecord =
                                new OkxBuyRecord(null, tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
                                        tradeDto.getPx().multiply(tradeDto.getSz()), BigDecimal.ZERO, BigDecimal.ZERO,
                                        OrderStatusEnum.PENDING.getStatus(), UUID.randomUUID().toString(), "", tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName,tradeDto.getMarketStatus(),tradeDto.getModeType(),null,null);
                        if (!strategyBusiness.checkBuy(buyRecord, coin, okxSettings, coinTotalBuyAmount)){
                            continue;
                        }
                        String okxOrderId = okxRequest(tradeDto, now, riseDto);
                        if (okxOrderId == null) {
                            continue;
                        }
                        buyRecord.setRemark(riseDto == null ? "" : riseDto.getRisePercent() + "||" + ticker.getIns());
                        buyRecord.setOkxOrderId(okxOrderId);
                        buyRecord.setCreateTime(now);
                        buyRecord.setUpdateTime(now);
                        buyRecordBusiness.save(buyRecord);
                        coinBusiness.markBuy(coin.getCoin(), accountId);
                    } else {
                        OkxSellRecord sellRecord = new OkxSellRecord(null, tradeDto.getBuyRecordId(), tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
                                tradeDto.getPx().multiply(tradeDto.getSz()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ZERO, OrderStatusEnum.PENDING.getStatus(),
                                UUID.randomUUID().toString(), "", tradeDto.getSellStrategyId(), tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName,null);
                        if (!strategyBusiness.checkSell(sellRecord, coin, tradeDto)) {
                            continue;
                        }
                        if (buyRecordBusiness.findOne(tradeDto.getBuyRecordId()).getStatus().intValue() != OrderStatusEnum.SUCCESS.getStatus()) {
                            continue;
                        }
                        String okxOrderId = okxRequest(tradeDto, now, riseDto);
                        if (okxOrderId == null) {
                            log.info("tradeDto：{}, okxOrderId：{}", JSON.toJSONString(tradeDto), okxOrderId);
                            continue;
                        }
                        sellRecord.setRemark(riseDto == null ? "" : riseDto.getRisePercent() + "||" + ticker.getIns());
                        sellRecord.setOkxOrderId(okxOrderId);
                        sellRecord.setCreateTime(now);
                        sellRecord.setUpdateTime(now);
                        sellRecord.setStatus(OrderStatusEnum.PENDING.getStatus());
                        if (sellRecordBusiness.save(sellRecord)) {
                            buyRecordBusiness.updateBySell(sellRecord.getBuyRecordId(), OrderStatusEnum.SELLING.getStatus());
                        }
                    }
                }
            }
            redisLock.releaseLock(lockKey);
        } catch (Exception e) {
            log.error("trade error",e);
            throw new ServiceException("交易异常", 500);
        }
    }


    public String okxRequest(TradeDto tradeDto, Date now, RiseDto riseDto) {

        Map<String, String> params = new HashMap(8);
        params.put("instId", tradeDto.getInstId());
        params.put("tdMode", "cash");
        params.put("clOrdId", TokenUtil.getOkxOrderId(now));
        params.put("side", tradeDto.getSide());
        params.put("ordType", tradeDto.getOrdType());
        params.put("sz", tradeDto.getSz().toString());
//        if (tradeDto.getSide().equalsIgnoreCase(OkxSideEnum.BUY.getSide())) {
        params.put("ordType", OkxOrdTypeEnum.LIMIT.getValue());
        params.put("px", tradeDto.getPx() + "");//setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP)
//        }
        String str = HttpUtil.postOkxV2("/api/v5/trade/order", params, riseDto.getApikey(), riseDto.getPassword(), riseDto.getSecretkey());
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





    public List<TradeDto> getTradeDtoV2( OkxCoin coin, OkxCoinTicker ticker, RiseDto riseDto,List<OkxBuyRecord> buyRecords,List<OkxSetting> okxSettings) {
        List<TradeDto> list = Lists.newArrayList();
        TradeDto tradeDto =  DtoUtils.transformBean(ticker, TradeDto.class);
        tradeDto.setUnit(coin.getUnit());
        tradeDto.setOrdType(riseDto.getOrderType());
        String modeType = riseDto.getModeType();



        //当前价格与标准值涨跌比
        BigDecimal ins = ticker.getLast().subtract(coin.getStandard()).divide(coin.getStandard(), 8, RoundingMode.HALF_UP);

        if (modeType.equals(ModeTypeEnum.GRID.getValue())) {
            //卖出
            tradeDto.setModeType(ModeTypeEnum.GRID.getValue());

            BigDecimal gridMinSellPercent = new BigDecimal(okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL)).findFirst().get().getSettingValue());
            buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.GRID.getValue()))
                    .filter(item -> (ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(), Constant.OKX_BIG_DECIMAL,RoundingMode.DOWN)).compareTo(gridMinSellPercent) > 0)
                    .forEach(item -> {
                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
                        temp.setMarketStatus(MarketStatusEnum.MAX_RISE.getStatus());
                        list.add(temp);
                    });


            //TODO - test
//            if (ins.compareTo(new BigDecimal(okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL)).findFirst().get().getSettingValue())) >= 0) {
//                buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.GRID.getValue())).forEach(item -> {
//                    TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
//                    temp.setMarketStatus(MarketStatusEnum.MAX_RISE.getStatus());
//                    list.add(temp);
//                });
//                return list;
//            }


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
//                    tradeDto.setPx(ticker.getLast().add(ticker.getLast().multiply(new BigDecimal(9.0E-4D))));
//                }
//                tradeDto.setSide(OkxSideEnum.BUY.getSide());
//                list.add(tradeDto);
//            }
        } else if (modeType.equals(ModeTypeEnum.MARKET.getValue())) {
            if (riseDto == null) {
                return list;
            }
            tradeDto.setModeType(ModeTypeEnum.MARKET.getValue());

            List<OkxBuyRecord> marketBuyRecords = buyRecords.stream().filter(item -> item.getModeType().equals(ModeTypeEnum.MARKET.getValue())).collect(Collectors.toList());
            //卖出 - 当天以前的订单
            Date todayMinTime = DateUtil.getMinTime(new Date());
            List<OkxBuyRecord> beforeBuyRecords = marketBuyRecords.stream().filter(item -> todayMinTime.getTime() > item.getCreateTime().getTime()).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(beforeBuyRecords)) {
                beforeBuyRecords.stream().forEach(item -> {
                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
                    if (riseIns.compareTo(new BigDecimal(okxSettings.stream().filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_LOW_SELL_PERCENT)).findFirst().get().getSettingValue())) > 0) {
                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
                        temp.setMarketStatus(MarketStatusEnum.RISE.getStatus());
                        list.add(temp);
                    }
                });
            }

            //卖出 - 当天上涨超过设置最大值
            if (CollectionUtils.isNotEmpty(marketBuyRecords) && riseDto.getRiseBought() == true
                    && riseDto.getRisePercent().compareTo(new BigDecimal(okxSettings.stream()
                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_RISE_MAX_SELL_PERCENT)).findFirst().get().getSettingValue())) > 0) {
                marketBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.RISE.getStatus()).forEach(item -> {
                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
                    if (riseIns.compareTo(new BigDecimal(0.02)) >= 0) {
                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
                        temp.setMarketStatus(MarketStatusEnum.MAX_RISE.getStatus());
                        list.add(temp);
                    }
                });
                riseDto.setSellPercent(riseDto.getRisePercent());
            }
            //卖出 - 当天上涨最大值百分比
            if (CollectionUtils.isNotEmpty(marketBuyRecords) && riseDto.getRiseBought() == true
                    && riseDto.getHighest().multiply(new BigDecimal(okxSettings.stream()
                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_RISE_SELL_PERCENT)).findFirst().get().getSettingValue())).compareTo(riseDto.getRisePercent()) >= 0)
            {
                marketBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.RISE.getStatus()).forEach(item -> {
                    BigDecimal riseIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8, RoundingMode.DOWN);
                    if (riseIns.compareTo(new BigDecimal(0.02)) >= 0) {
                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
                        temp.setMarketStatus(MarketStatusEnum.RISE.getStatus());
                        list.add(temp);
                        riseDto.setSellPercent(riseDto.getRisePercent());
                    }
                });
            }

            //卖出 —— 大盘下跌时买入的
            List<OkxBuyRecord> tempFallBuyRecords = marketBuyRecords.stream().filter(obj -> obj.getMarketStatus() == MarketStatusEnum.FALL.getStatus()).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(tempFallBuyRecords)) {
                tempFallBuyRecords.stream().forEach(item -> {
                    BigDecimal currentIns = ticker.getLast().subtract(item.getPrice()).divide(item.getPrice(),8,RoundingMode.DOWN);
                    if (currentIns.compareTo(new BigDecimal(okxSettings.stream()
                            .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_LOW_SELL_PERCENT)).findFirst().get().getSettingValue())) > 0){
                        TradeDto temp =  getSellDto(tradeDto,ticker,coin,item);
                        temp.setMarketStatus(MarketStatusEnum.FALL.getStatus());
                        list.add(temp);
                    }
                });
            }

            //当前帐号该币种已买
            if (coinBusiness.checkBoughtCoin(coin.getCoin(), riseDto.getAccountId()) == true) {
                return list;
            }

//            Integer marketBuyTimes = Integer.valueOf(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_BUY_TIMES)).findFirst().get().getSettingValue());
//            BigDecimal marketRiseBuyPercent = new BigDecimal(okxSettings.stream()
//                    .filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_RISE_BUY_PERCENT)).findFirst().get().getSettingValue());

            //买入- 大盘上涨 效果不好
//            if ( coin.getStatus() == CoinStatusEnum.OPEN.getStatus()
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

            //买入- 大盘下跌
            if (coin.getStatus() == CoinStatusEnum.OPEN.getStatus() && ins.compareTo(BigDecimal.ZERO) <= 0 //当前价格小于等于标准值
                    && riseDto.getLowPercent().compareTo(new BigDecimal(okxSettings.stream().filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_LOW_BUY_PERCENT)).findFirst().get().getSettingValue())) > 0
                    && new BigDecimal(okxSettings.stream().filter(obj -> obj.getSettingKey().equals(OkxConstants.MARKET_BTC_FALL_INS)).findFirst().get().getSettingValue()).compareTo(riseDto.getBTCIns()) > 0 ) {
                //买入数量
                BigDecimal buySz = getBuySz(okxSettings, ticker, ins);
                if (buySz.compareTo(BigDecimal.ZERO) <= 0) {
                    return list;
                }
                tradeDto.setTimes(0);
                tradeDto.setSz(buySz);
                tradeDto.setPx(ticker.getLast());

                //设置价格
                if (OkxOrdTypeEnum.LIMIT.getValue().equals(tradeDto.getOrdType())) {
                    tradeDto.setPx(ticker.getLast().add(ticker.getLast().multiply(new BigDecimal(9.0E-4D))));
                }
                tradeDto.setMarketStatus(MarketStatusEnum.FALL.getStatus());
                tradeDto.setSide(OkxSideEnum.BUY.getSide());
                tradeDto.setBuyStrategyId(0);
                list.add(tradeDto);
            }
        }
        return list.size() <= 1 ? list : list.stream().collect(Collectors.collectingAndThen(
                Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(TradeDto::getBuyRecordId))), ArrayList::new ));
    }

    /**
     * 买入数量
     * @param okxSettings
     * @param ticker
     * @return
     */
    public BigDecimal getBuySz(List<OkxSetting> okxSettings, OkxCoinTicker ticker, BigDecimal ins) {
        List<OkxSetting> amountSettings = okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.BUY_MARK_COIN_FALL_AMOUNT)).sorted(Comparator.comparing(OkxSetting::getSettingValue)).collect(Collectors.toList());
        List<OkxSetting> fallPercentSettings = okxSettings.stream().filter(item -> item.getSettingKey().equals(OkxConstants.BUY_MARK_COIN_FALL_PERCENT)).sorted(Comparator.comparing(OkxSetting::getSettingValue)).collect(Collectors.toList());

        BigDecimal buyUsdtAmout = BigDecimal.ZERO;
        for (int i = 0; i < amountSettings.size(); i++) {
            if (ins.abs().compareTo(new BigDecimal(fallPercentSettings.get(i).getSettingValue())) >= 0) {
                buyUsdtAmout = new BigDecimal(amountSettings.get(i).getSettingKey());
            }
        }
        if (buyUsdtAmout.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        //买入数量
        BigDecimal buySz = buyUsdtAmout.divide(ticker.getLast(), Constant.OKX_BIG_DECIMAL, RoundingMode.DOWN);
        //设置小数位
        Utils.setScale(buySz);
        return buySz;
    }


    private TradeDto getSellDto (TradeDto tradeDto,OkxCoinTicker ticker,OkxCoin coin,OkxBuyRecord item) {
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
        return temp;
    }
}
