package com.ruoyi.okx.business;


import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.enums.Status;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.core.utils.TokenUtil;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.enums.*;
import com.ruoyi.okx.params.dto.RiseDto;
import com.ruoyi.okx.params.dto.TradeDto;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.DtoUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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
    private RedisService redisService;

    /**
     * 买卖交易
     * @param list
     * @param coin
     * @param map
     * @throws Exception
     */
    public void trade(List<TradeDto> list, OkxCoin coin, Map<String, String> map) throws Exception {
        Date now = new Date();
        Integer accountId = Integer.valueOf(map.get("id"));
        String accountName = map.get("accountName");
        for (TradeDto tradeDto:list) {
            if (tradeDto.getSide().equals(OkxSideEnum.BUY.getSide())) {
                OkxBuyRecord buyRecord =
                        new OkxBuyRecord(null, tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
                                tradeDto.getPx().multiply(tradeDto.getSz()).setScale(8, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ZERO,
                                OrderStatusEnum.PENDING.getStatus(), UUID.randomUUID().toString(), "", tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName);
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
                log.info("buyRecord：{}", JSON.toJSONString(buyRecord));
            } else {
                OkxSellRecord sellRecord = new OkxSellRecord(null, tradeDto.getBuyRecordId(), tradeDto.getCoin(), tradeDto.getInstId(), tradeDto.getPx(), tradeDto.getSz(),
                        tradeDto.getPx().multiply(tradeDto.getSz()).setScale(8, RoundingMode.HALF_UP), BigDecimal.ZERO, OrderStatusEnum.PENDING.getStatus(),
                        UUID.randomUUID().toString(), "", tradeDto.getSellStrategyId(), tradeDto.getBuyStrategyId(), tradeDto.getTimes(), accountId,accountName);
                if (!strategyBusiness.checkSell(sellRecord, coin, tradeDto))
                    return;
                String okxOrderId = tradeOkx(tradeDto, now, map);
                if (okxOrderId == null) {
                    log.info("tradeDto：{}, okxOrderId：{}", JSON.toJSONString(tradeDto), okxOrderId);
                    return;
                }
                sellRecord.setOkxOrderId(okxOrderId);
                sellRecord.setCreateTime(now);
                sellRecord.setUpdateTime(now);
                sellRecord.setStatus(OrderStatusEnum.PENDING.getStatus());
                try {
                    if (this.sellRecordBusiness.save(sellRecord)) {
                        this.buyRecordBusiness.updateBySell(sellRecord.getBuyRecordId(), OrderStatusEnum.SELLING.getStatus());
                        log.info("sellRecord:{}", JSON.toJSONString(sellRecord));
                    }
                } catch (Exception e) {
                    log.error("sellRecord:{},tradeDto:{}", JSON.toJSONString(sellRecord), JSON.toJSONString(tradeDto));
                    e.printStackTrace();
                }
            }
        }

    }

    public String tradeOkx(TradeDto tradeDto, Date now, Map<String, String> map) {
        Map<String, String> params = new HashMap<>(8);
        params.put("instId", tradeDto.getInstId());
        params.put("tdMode", "cash");
        params.put("clOrdId", TokenUtil.getOkxOrderId(now));
        params.put("side", tradeDto.getSide());
        params.put("ordType", tradeDto.getOrdType());
        params.put("sz", tradeDto.getSz().toString());
        params.put("px", tradeDto.getPx().setScale(6, RoundingMode.HALF_UP) + "");
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


    @Async
    public void trade(List<OkxCoin> coins, List<OkxCoinTicker> tickers, Map<String, String> map) throws ServiceException {
        try {
            boolean hasTrade = false;
            //赋值用户订单类型和交易模式
            strategyBusiness.listByAccountId(Integer.valueOf(map.get("id"))).stream()
                    .filter(item -> item.getSettingKey().equals(OkxConstants.ORD_TYPE) || item.getSettingKey().equals(OkxConstants.MODE_TYPE))
                    .collect(Collectors.toList()).stream().forEach(obj -> map.put(obj.getSettingKey(), obj.getSettingValue()));

            for (OkxCoinTicker ticker : tickers) {
                Optional<OkxCoin> OkxCoin = coins.stream().filter(obj -> obj.getCoin().equals(ticker.getCoin())).findFirst();
                if (!OkxCoin.isPresent()
                        || OkxCoin.get().getStandard().compareTo(BigDecimal.ZERO) <= 0
                        || OkxCoin.get().getStatus().intValue() == CoinStatusEnum.CLOSE.getStatus().intValue()) {
                    continue;
                }
                BigDecimal ins = ticker.getLast().subtract(OkxCoin.get().getStandard()).divide(OkxCoin.get().getStandard(), 8, RoundingMode.HALF_UP);

                //获取交易参数
                List<TradeDto> list = getTradeDto(ins, OkxCoin.get(), ticker, map);
                if (CollectionUtils.isEmpty(list)) {
                    return;
                }
                //交易
                trade(list, OkxCoin.get(), map);
                hasTrade = true;
            }
            //大盘交易
            if (hasTrade && map.get(OkxConstants.MODE_TYPE).equals(ModeTypeEnum.MARKET.getValue())) {
                RiseDto riseDto = redisService.getCacheObject(RedisConstants.OKX_TICKER_MARKET);
                riseDto.setStatus(Integer.parseInt(Status.DISABLE.getCode()));
                redisService.setCacheObject(RedisConstants.OKX_TICKER_MARKET, riseDto);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServiceException("交易异常", 500);
        }
    }

    private List<TradeDto> getTradeDto(BigDecimal ins, OkxCoin coin, OkxCoinTicker ticker, Map<String, String> map) {
        List<TradeDto> list = Lists.newArrayList();
        TradeDto tradeDto =  DtoUtils.transformBean(ticker, TradeDto.class);
        tradeDto.setUnit(coin.getUnit());
        tradeDto.setOrdType(map.get(OkxConstants.ORD_TYPE));

        Integer accountId = Integer.valueOf(map.get("id"));
        ModeTypeEnum modeType = strategyBusiness.getModeType(accountId);
        if (modeType == ModeTypeEnum.GRID) {
            //卖出
            if (ins.compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.GRIDE_MIN_PERCENT_FOR_SELL))) >= 0) {
                buyRecordBusiness.findSuccessRecord(coin.getCoin(), accountId).stream().forEach(item -> {
                    TradeDto temp =  DtoUtils.transformBean(tradeDto, TradeDto.class);
                    temp.setSz(item.getQuantity().subtract(item.getFee().abs()).setScale(8, RoundingMode.HALF_UP));
                    temp.setTimes(item.getTimes());
                    temp.setBuyStrategyId(item.getStrategyId());
                    temp.setSide(OkxSideEnum.SELL.getSide());
                    temp.setBuyRecordId(item.getId());
                    BigDecimal price = ticker.getLast().subtract(coin.getStandard().multiply(new BigDecimal(9.0E-4D)));
                    if (OkxOrdTypeEnum.LIMIT.getSide().equals(temp.getOrdType())) {
                        temp.setPx(price.setScale(8, RoundingMode.HALF_UP));
                    }
                    list.add(temp);
                });
                return list;
            }
            //买入
            if ((new BigDecimal(-0.011D)).compareTo(ins) >= 0) {
                OkxBuyStrategy buyStrategy = this.buyStrategyBusiness.list().stream().filter(sell -> (sell.getFallPercent().compareTo(ins.abs().setScale(2, RoundingMode.DOWN)) >= 0)).filter(sell -> sell.getAccountId().equals(accountId)).sorted(Comparator.comparing(OkxBuyStrategy::getFallPercent)).findFirst().get();
                tradeDto.setSz(coin.getUnit().multiply(BigDecimal.valueOf(buyStrategy.getTimes())).setScale(8, RoundingMode.HALF_UP));
                tradeDto.setTimes(buyStrategy.getTimes());

                //设置价格
                if (OkxOrdTypeEnum.LIMIT.getSide().equals(tradeDto.getOrdType())) {
                    BigDecimal buyPrice = ticker.getLast().add(ticker.getLast().multiply(new BigDecimal("9.0E-4")));
                    tradeDto.setPx(buyPrice.setScale(8, RoundingMode.HALF_UP));
                }
                tradeDto.setSide(OkxSideEnum.BUY.getSide());
                list.add(tradeDto);
            }
        } else if (modeType == ModeTypeEnum.MARKET) {
            RiseDto riseDto = redisService.getCacheObject(RedisConstants.OKX_TICKER_MARKET);
            if (riseDto == null) {
                return list;
            }
            //卖出
            if (riseDto.getRisePercent().compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.MARKET_RISE_MAX_SELL_PERCENT))) > 0
                || riseDto.getHighest().multiply(new BigDecimal(settingService.selectSettingByKey(OkxConstants.MARKET_RISE_SELL_PERCENT))).compareTo(riseDto.getRisePercent()) <= 0) {
                buyRecordBusiness.findSuccessRecord(coin.getCoin(), accountId).stream().forEach(item -> {
                    TradeDto temp =  DtoUtils.transformBean(tradeDto, TradeDto.class);
                    temp.setSz(item.getQuantity().subtract(item.getFee().abs()).setScale(8, RoundingMode.HALF_UP));
                    temp.setTimes(item.getTimes());
                    temp.setBuyStrategyId(item.getStrategyId());
                    temp.setSide(OkxSideEnum.SELL.getSide());
                    temp.setBuyRecordId(item.getId());
                    BigDecimal price = ticker.getLast().subtract(coin.getStandard().multiply(new BigDecimal(9.0E-4D)));
                    if (OkxOrdTypeEnum.LIMIT.getSide().equals(temp.getOrdType())) {
                        temp.setPx(price.setScale(8, RoundingMode.HALF_UP));
                    }
                    list.add(temp);
                });
                return list;
            }
            //买入
            if (riseDto.getRisePercent().compareTo(new BigDecimal(settingService.selectSettingByKey(OkxConstants.MARKET_RISE_BUY_PERCENT))) > 0) {
                tradeDto.setTimes(Integer.valueOf(settingService.selectSettingByKey(OkxConstants.MARKET_BUY_TIMES)));
                tradeDto.setSz(coin.getUnit().multiply(BigDecimal.valueOf(tradeDto.getTimes())).setScale(8, RoundingMode.HALF_UP));
                //设置价格
                if (OkxOrdTypeEnum.LIMIT.getSide().equals(tradeDto.getOrdType())) {
                    BigDecimal buyPrice = ticker.getLast().add(ticker.getLast().multiply(new BigDecimal(9.0E-4D)));
                    tradeDto.setPx(buyPrice.setScale(8, RoundingMode.HALF_UP));
                }
                tradeDto.setSide(OkxSideEnum.BUY.getSide());
                list.add(tradeDto);
            }
        }
        return list;
    }

    public String buyNewCoin(OkxCoinTicker ticker, Map<String, String> map) {
        Date now = new Date();
        BigDecimal sz = new BigDecimal(OkxConstants.NEW_COIN_MAX_USDT).divide(ticker.getLast(), 4, RoundingMode.DOWN);
        Map<String, String> params = new HashMap<>(8);
        params.put("instId", ticker.getInstId());
        params.put("tdMode", "cash");
        params.put("clOrdId", TokenUtil.getOkxOrderId(now));
        params.put("side", "buy");
        params.put("ordType", "market");
        params.put("sz", sz.toString());
        String str = HttpUtil.postOkx("/api/v5/trade/order", params, map);
        JSONObject json = JSONObject.parseObject(str);
        if (json == null || !json.getString("code").equals("0")) {
            log.error("param:{},str:{},px:{}", JSON.toJSONString(params), str, ticker.getLast());
            return null;
        }
        JSONObject dataJSON = json.getJSONArray("data").getJSONObject(0);
        if (dataJSON == null || !dataJSON.getString("sCode").equals("0")) {
            log.error("param:{},str:{},px:{}", JSON.toJSONString(params), str, ticker.getLast());
            return null;
        }
        OkxBuyRecord buyRecord = new OkxBuyRecord(null, ticker.getCoin(), ticker.getInstId(), ticker.getLast(), sz, new BigDecimal(OkxConstants.NEW_COIN_MAX_USDT), BigDecimal.ZERO, BigDecimal.ZERO, OrderStatusEnum.NEW.getStatus(), UUID.randomUUID().toString(), dataJSON.getString("ordId"), Integer.valueOf(0), 0, Integer.valueOf(map.get("id")),map.get("accountName"));
        buyRecord.setCreateTime(now);
        buyRecord.setUpdateTime(now);
        this.buyRecordBusiness.save(buyRecord);
        return dataJSON.getString("ordId");
    }


}
