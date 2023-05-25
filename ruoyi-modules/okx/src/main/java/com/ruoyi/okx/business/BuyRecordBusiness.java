package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.domain.OkxCoinTicker;
import com.ruoyi.okx.enums.OrderStatusEnum;
import com.ruoyi.okx.mapper.BuyRecordMapper;
import com.ruoyi.okx.params.DO.BuyRecordDO;
import com.ruoyi.okx.utils.Constant;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BuyRecordBusiness extends ServiceImpl<BuyRecordMapper, OkxBuyRecord> {
    private static final Logger log = LoggerFactory.getLogger(BuyRecordBusiness.class);

    @Resource
    private BuyRecordMapper buyRecordMapper;

    @Resource
    private CoinBusiness coinBusiness;

    @Resource
    private CommonBusiness commonBusiness;

    @Resource
    private AccountBalanceBusiness accountBalanceBusiness;

    @Resource
    @Lazy
    private TickerBusiness tickerBusiness;

    @Resource
    private RedisService redisService;

    public List<OkxBuyRecord> selectList(BuyRecordDO buyRecordDO) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq((null != buyRecordDO.getCoin()), OkxBuyRecord::getCoin, buyRecordDO.getCoin());
        wrapper.eq((null != buyRecordDO.getAccountName()), OkxBuyRecord::getAccountName, buyRecordDO.getAccountName());
        wrapper.eq((null != buyRecordDO.getStatus()), OkxBuyRecord::getStatus, buyRecordDO.getStatus());
        wrapper.between((buyRecordDO.getParams().get("beginTime") != null), OkxBuyRecord::getCreateTime, buyRecordDO.getParams().get("beginTime"), buyRecordDO.getParams().get("endTime"));
        List<OkxBuyRecord> list =buyRecordMapper.selectList(wrapper);
        List<OkxCoinTicker> tickerList = tickerBusiness.findTodayTicker();
        for (OkxBuyRecord record:list) {
            tickerList.stream().filter(item -> item.getCoin().equals(record.getCoin())).findFirst().ifPresent(obj -> {
                record.setLast(obj.getLast());
            });
        }
        return list;
    }

    public List<OkxBuyRecord> findSuccessRecord(String coin, Integer accountId, String modeType, Integer marketStatus) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.in(OkxBuyRecord::getStatus, Arrays.asList(new Integer[] { OrderStatusEnum.SUCCESS.getStatus(), OrderStatusEnum.PARTIALLYFILLED.getStatus() }));
        wrapper.eq((StringUtils.isNotEmpty(coin)), OkxBuyRecord::getCoin, coin);
        wrapper.eq((accountId != null), OkxBuyRecord::getAccountId, accountId);
        wrapper.eq((StringUtils.isNotEmpty(modeType)), OkxBuyRecord::getModeType, modeType);
        wrapper.eq((marketStatus != null), OkxBuyRecord::getMarketStatus, marketStatus);
        return buyRecordMapper.selectList(wrapper);
    }

    public boolean hasBuy(Integer accountId, Integer strategyId, String coin,String modeType) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxBuyRecord::getStrategyId, strategyId);
        wrapper.eq(OkxBuyRecord::getAccountId, accountId);
        wrapper.eq(OkxBuyRecord::getCoin, coin);
        wrapper.eq((StringUtils.isNotEmpty(modeType)), OkxBuyRecord::getModeType, modeType);
        wrapper.in(OkxBuyRecord::getStatus, OrderStatusEnum.getUnFinishList());
        wrapper.ge(OkxBuyRecord::getCreateTime, DateUtil.parseSimpleDateTime("2000-1-1 00:00:00"));
        List<OkxBuyRecord> buyRecords = buyRecordMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(buyRecords)) {
            return false;
        }
        return true;
    }

    public int getTimes(Integer accountId, String coin) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxBuyRecord::getAccountId, accountId);
        wrapper.eq(OkxBuyRecord::getCoin, coin);
        wrapper.in(OkxBuyRecord::getStatus, OrderStatusEnum.getUnFinishList());
        Long count = buyRecordMapper.selectCount(wrapper);
        return count.intValue();
    }

    public List<OkxBuyRecord> findPendings(Integer accountId) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxBuyRecord::getStatus, OrderStatusEnum.PENDING.getStatus());
        if (accountId != null && accountId > 0) {
            wrapper.eq(OkxBuyRecord::getAccountId, accountId);
        }
        return buyRecordMapper.selectList(wrapper);
    }

    public List<OkxBuyRecord> findPARTIALLYFILLED(Integer accountId) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxBuyRecord::getStatus, OrderStatusEnum.PARTIALLYFILLED.getStatus());
        if (accountId != null && accountId > 0) {
            wrapper.eq(OkxBuyRecord::getAccountId, accountId);
        }
        return buyRecordMapper.selectList(wrapper);
    }

    public boolean updateBySell(Integer buyRecordId, Integer status) {
        OkxBuyRecord buyRecord = getById(buyRecordId);
        if (buyRecord == null) {
            log.error("更新买入记录异常:{}", buyRecordId);
            return false;
        }
        buyRecord.setStatus(status);
        buyRecord.setUpdateTime(new Date());
        return updateById(buyRecord);
    }

    public List<OkxBuyRecord> findUnfinish(Integer accountId, Integer hours) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.in(OkxBuyRecord::getStatus, Arrays.asList(new Integer[] { OrderStatusEnum.CREATED.getStatus(), OrderStatusEnum.SUCCESS.getStatus(), OrderStatusEnum.PENDING.getStatus() }));
        if (hours != null && hours.intValue() > 0)
            wrapper.gt(OkxBuyRecord::getCreateTime, DateUtil.addHour(new Date(), -hours.intValue()));
        wrapper.eq(OkxBuyRecord::getAccountId, accountId);
        return buyRecordMapper.selectList(wrapper);
    }

//    public List<OkbBuyRecord> findByStatus(Integer accountId, List<Integer> status) {
//        LambdaQueryWrapper<OkbBuyRecord> wrapper = new LambdaQueryWrapper();
//        wrapper.in(OkbBuyRecord::getStatus, status);
//        wrapper.eq(OkbBuyRecord::getAccountId, accountId);
//        return this.buyRecordMapper.selectList((Wrapper)wrapper);
//    }

    public boolean update(OkxBuyRecord record) {
        return buyRecordMapper.updateById(record) > 0 ? true : false;
    }

    public OkxBuyRecord findOne(Integer id) {
        return buyRecordMapper.selectById(id);
    }

    public boolean todayHadBuy() {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        Date now = new Date();
        wrapper.between( OkxBuyRecord::getCreateTime, DateUtil.getMinTime(now), now);
        return CollectionUtils.isNotEmpty(buyRecordMapper.selectList(wrapper));
    }

    @Async
    public void syncBuyOrder(Map<String, String> map) {
        this.syncOrderStatus(map);
    }
    @Transactional(rollbackFor = {Exception.class})
    public void syncOrderStatus(Map<String, String> map) throws ServiceException {
        try {
            //未完成订单
            List<OkxBuyRecord> pendings = findPendings(Integer.valueOf(map.get("id")));
            List<OkxBuyRecord> pARTIALLYFILLED = findPARTIALLYFILLED(Integer.valueOf(map.get("id")));
            List<OkxBuyRecord> list = Lists.newArrayList();
            list.addAll(pendings);
            list.addAll(pARTIALLYFILLED);
            Date now = new Date();
            for (OkxBuyRecord buyRecord:list) {
                String str = HttpUtil.getOkx("/api/v5/trade/order?instId=" + buyRecord.getInstId() + "&ordId=" + buyRecord.getOkxOrderId(), null, map);
                if (org.apache.commons.lang.StringUtils.isEmpty(str)) {
                    log.error("查询订单状态异常{}", str);
                }
                JSONObject json = JSONObject.parseObject(str);
                if (json == null || !json.getString("code").equals("0")) {
                    log.error("下单异常params:{} :{}", JSON.toJSONString(buyRecord), (json == null) ? "null" : json.toJSONString());
                    return;
                }
                JSONObject data = json.getJSONArray("data").getJSONObject(0);
                buyRecord.setStatus((commonBusiness.getOrderStatus(data.getString("state")) == null) ? buyRecord.getStatus() : commonBusiness.getOrderStatus(data.getString("state")));
                if (buyRecord.getStatus().equals(OrderStatusEnum.PENDING.getStatus()) && DateUtil.diffDay(DateUtil.getMinTime(buyRecord.getCreateTime()), DateUtil.getMinTime(now)) > 0) {
                    boolean cancelOrder = cancelOrder(buyRecord.getInstId(), buyRecord.getOkxOrderId(), map);
                    if (cancelOrder) {
                        buyRecord.setStatus(OrderStatusEnum.CANCEL.getStatus());
                        log.info("订单买入超过1天自动撤销");
                        updateById(buyRecord);
                        return;
                    }
                }
                if (buyRecord.getStatus().intValue() == OrderStatusEnum.SUCCESS.getStatus()) {
                    //同步手续费
                    syncOrderFee(map, buyRecord);
                    accountBalanceBusiness.addCount(buyRecord.getCoin(), buyRecord.getAccountId(), buyRecord.getQuantity());
                }
                Thread.sleep(50);
            }
        } catch (Exception e) {
            log.error("同步订单异常", e);
            throw new ServiceException("同步订单异常");
        }
    }

    public boolean syncOrderFee(Map<String, String> map,OkxBuyRecord buyRecord) {
        try {
            String str = HttpUtil.getOkx("/api/v5/trade/fills?instType=SPOT&ordId=" + buyRecord.getOkxOrderId(), null, map);
            JSONObject json = JSONObject.parseObject(str);
            if (json == null || !json.getString("code").equals("0")) {
                log.error("查询订单状态异常:{}", (json == null) ? "null" : json.toJSONString());
                markSyncOrderFeeFail(buyRecord);
                return false;
            }
            JSONArray dataArray = json.getJSONArray("data");
            if (dataArray.size() <= 0) {
                log.error("查询订单返回数据异常params:{}, res:{}",buyRecord.getOkxOrderId(), str);
                markSyncOrderFeeFail(buyRecord);
                return false;
            }
            BigDecimal fee = BigDecimal.ZERO;
            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject jsonObject = dataArray.getJSONObject(i);
                fee = fee.add(jsonObject.getBigDecimal("fee").abs());
            }
            if (buyRecord.getFee().compareTo(fee.abs()) != 0) {
                log.info("同步订单手续费:{},buyRecord:{}",fee, buyRecord.getFee());
            }
            buyRecord.setFee(fee.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
            buyRecord.setFeeUsdt(buyRecord.getFee().multiply(buyRecord.getPrice()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
            updateById(buyRecord);
        } catch (Exception e) {
            log.error("syncOrderFee error {}",e);
        }
        return true;
    }

    private void markSyncOrderFeeFail(OkxBuyRecord buyRecord) {
        redisService.setCacheObject(CacheConstants.OKX_BUY_ORDER_AGAIN + buyRecord.getId(), buyRecord);
        int i = 1;
        while (redisService.hasKey(CacheConstants.OKX_BUY_ORDER_AGAIN + buyRecord.getId()) == false && i < 10) {
            i++;
            redisService.setCacheObject(CacheConstants.OKX_BUY_ORDER_AGAIN + buyRecord.getId(), buyRecord);
            if (i == 10 ) {
                log.error("syncOrderFeeAgain fail buyRecordId:{}",buyRecord.getId());
            }
        }
    }


    @Async
    public void syncOrderFeeAgain( Map<String, String> map) {
        try {
            Collection<String> keys = redisService.keys(CacheConstants.OKX_BUY_ORDER_AGAIN + "*");
            if (CollectionUtils.isNotEmpty(keys)) {
                for (String key:keys) {
                    OkxBuyRecord buyRecord = redisService.getCacheObject(key);
                    if (buyRecord.getAccountId().equals(map.get("id"))) {
                        if(syncOrderFee(map, buyRecord)) {
                            redisService.deleteObject(key);
                            Thread.sleep(50);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("syncOrderFeeAgain err :" ,e);
        }
    }



    public boolean cancelOrder(String instId, String okxOrderId, Map<String, String> map) {
        Map<String, String> params = new HashMap<>(2);
        params.put("instId", instId);
        params.put("ordId", okxOrderId);
        String cancelStr = HttpUtil.postOkx("/api/v5/trade/cancel-order", params, map);
        JSONObject cancelJson = JSONObject.parseObject(cancelStr);
        if (cancelJson == null || !cancelJson.getString("code").equals("0")) {
            log.error("撤销订单失败:{}", JSON.toJSONString(params));
            return false;
        }
        JSONObject dataJSON = cancelJson.getJSONArray("data").getJSONObject(0);
        if (dataJSON == null || !dataJSON.getString("sCode").equals("0")) {
            log.error("撤销订单失败:{}", JSON.toJSONString(params));
            return false;
        }
        return true;
    }
}
