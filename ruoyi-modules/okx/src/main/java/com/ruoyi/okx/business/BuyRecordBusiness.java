package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.domain.OkxCoin;
import com.ruoyi.okx.domain.OkxCoinProfit;
import com.ruoyi.okx.domain.OkxCoinTicker;
import com.ruoyi.okx.enums.OrderStatusEnum;
import com.ruoyi.okx.mapper.BuyRecordMapper;
import com.ruoyi.okx.params.DO.BuyRecordDO;
import com.ruoyi.okx.utils.Constant;
import io.swagger.models.auth.In;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private RedisLock redisLock;

    public List<OkxBuyRecord> selectList(BuyRecordDO buyRecordDO) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq((null != buyRecordDO.getCoin()), OkxBuyRecord::getCoin, buyRecordDO.getCoin());
        wrapper.eq((null != buyRecordDO.getAccountName()), OkxBuyRecord::getAccountName, buyRecordDO.getAccountName());
        wrapper.eq((null != buyRecordDO.getStatus()), OkxBuyRecord::getStatus, buyRecordDO.getStatus());
        wrapper.between((buyRecordDO.getParams().get("beginTime") != null), OkxBuyRecord::getCreateTime, buyRecordDO.getParams().get("beginTime"), buyRecordDO.getParams().get("endTime"));
        wrapper.orderByDesc(OkxBuyRecord::getUpdateTime);
        List<OkxBuyRecord> list =buyRecordMapper.selectList(wrapper);
        for (OkxBuyRecord record:list) {
            OkxCoinTicker okxCoinTicker = tickerBusiness.getTickerCache(record.getCoin());
            record.setLast(okxCoinTicker.getLast());
            if (record.getStatus().intValue() == OrderStatusEnum.SUCCESS.getStatus()) {
                record.setProfit(record.getLast().subtract(record.getPrice()).multiply(record.getQuantity()).setScale(Constant.OKX_BIG_DECIMAL,RoundingMode.DOWN));
            }
        }
        return list;
    }

    public List<OkxBuyRecord> findSuccessRecord() {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxBuyRecord::getStatus, OrderStatusEnum.SUCCESS.getStatus());
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

    public List<OkxBuyRecord> findByAccountAndCoin(Integer accountId,String coin) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq(accountId != null, OkxBuyRecord::getAccountId, accountId);
        wrapper.eq(StringUtils.isNotEmpty(coin), OkxBuyRecord::getCoin, coin);
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

    public List<OkxBuyRecord> findSyncList(Integer accountId) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.in(OkxBuyRecord::getStatus, OrderStatusEnum.PARTIALLYFILLED.getStatus(), OrderStatusEnum.PENDING.getStatus());
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
            List<OkxBuyRecord> list = getPageDate(Integer.valueOf(map.get("id")));
            log.info("syncOrderStatus_list_size:{}",list.size());

            Date now = new Date();
            String lockKey = "";
            for (OkxBuyRecord buyRecord:list) {
                lockKey = RedisConstants.OKX_SYNC_BUY_ORDER + buyRecord.getId();
                boolean lock = redisLock.lock(lockKey,30,3,2000);
                if (lock == false) {
                    log.error("tradeV2获取锁失败，交易取消 lockKey:{}",lockKey);
                    continue;
                }
                String str = HttpUtil.getOkx("/api/v5/trade/order?instId=" + buyRecord.getInstId() + "&ordId=" + buyRecord.getOkxOrderId(), null, map);
                if (org.apache.commons.lang.StringUtils.isEmpty(str)) {
                    log.error("查询订单状态异常{}", str);
                    continue;
                }
                JSONObject json = JSONObject.parseObject(str);
                if (json == null || !json.getString("code").equals("0")) {
                    log.error("下单异常params:{} :{}", JSON.toJSONString(buyRecord), (json == null) ? "null" : json.toJSONString());
                    continue;
                }
                JSONObject data = json.getJSONArray("data").getJSONObject(0);
                buyRecord.setStatus((commonBusiness.getOrderStatus(data.getString("state")) == null) ? buyRecord.getStatus() : commonBusiness.getOrderStatus(data.getString("state")));
                if (buyRecord.getStatus().equals(OrderStatusEnum.PENDING.getStatus()) && DateUtil.diffMins(buyRecord.getCreateTime(), now) >= 10) {
                    if (cancelOrder(buyRecord.getInstId(), buyRecord.getOkxOrderId(), map)) {
                        buyRecord.setStatus(OrderStatusEnum.CANCEL.getStatus());
                        updateById(buyRecord);
                        log.info("订单买入超过10分钟自动撤销");

                        coinBusiness.cancelBuy(buyRecord.getCoin(), buyRecord.getAccountId());
                        continue;
                    }
                }
                if (buyRecord.getStatus().intValue() == OrderStatusEnum.SUCCESS.getStatus()) {
                    //同步手续费
                    syncOrderFee(map, buyRecord);
//                    accountBalanceBusiness.addCount(buyRecord.getCoin(), buyRecord.getAccountId(), buyRecord.getQuantity());
//                    accountBusiness.getAccountMap(accountName);
                    accountBalanceBusiness.syncAccountBalance(map, buyRecord.getCoin(),  now);
                }
                Thread.sleep(50);
                redisLock.releaseLock(lockKey);
            }
        } catch (Exception e) {
            log.error("同步订单异常", e);
            throw new ServiceException("同步订单异常");
        }
    }

    private List<OkxBuyRecord> getPageDate(Integer accountId) {
        List<OkxBuyRecord> buyRecords = com.google.common.collect.Lists.newArrayList();

        int page = 1;
        PageHelper.startPage(page, 30, "create_time");

        List<OkxBuyRecord> accountBuyRecords = findSyncList(accountId);
        buyRecords.addAll(accountBuyRecords);
        Integer pages = new PageInfo(accountBuyRecords).getPages();
        while (pages > page) {
            page++;
            PageHelper.startPage(page, 30, "create_time");
            accountBuyRecords = findSyncList(accountId);
            buyRecords.addAll(accountBuyRecords);
        }
        return buyRecords;
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
            BigDecimal quantity = BigDecimal.ZERO;
            BigDecimal amount = BigDecimal.ZERO;
            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject jsonObject = dataArray.getJSONObject(i);
                fee = fee.add(jsonObject.getBigDecimal("fee").abs());
                quantity = quantity.add(jsonObject.getBigDecimal("fillSz"));
                amount = amount.add(jsonObject.getBigDecimal("fillSz").multiply(jsonObject.getBigDecimal("fillPx")).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
            }
            BigDecimal price = amount.divide(quantity, Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP);
            if (buyRecord.getFee().compareTo(fee.abs()) != 0) {
                log.info("同步买入订单手续费:{},buyRecord:{}",fee, buyRecord.getFee());
            }
            buyRecord.setFee(fee.setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
            buyRecord.setFeeUsdt(buyRecord.getFee().multiply(buyRecord.getPrice()).setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP));
            buyRecord.setQuantity(quantity);
            buyRecord.setPrice(price);
            buyRecord.setAmount(amount);
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

    @Async
    public void updateCoinTurnOver (String coin) {
        try {
            Thread.sleep(3000);
            OkxCoin okxCoin = coinBusiness.findOne(coin);
            List<OkxBuyRecord> buyRecords = findByAccountAndCoin(null, coin).stream()
                    .filter(item -> item.getStatus().intValue() != OrderStatusEnum.CANCEL.getStatus())
                    .filter(item -> item.getStatus().intValue() != OrderStatusEnum.FAIL.getStatus())
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(buyRecords)) {
                return;
            }
            Integer finishCount = (int) buyRecords.stream().filter(item -> item.getStatus().intValue() == OrderStatusEnum.FINISH.getStatus()).count();
            okxCoin.setTurnOver(new BigDecimal(finishCount).divide(new BigDecimal(buyRecords.size()), 4, RoundingMode.DOWN));
            coinBusiness.updateById(okxCoin);
            coinBusiness.updateCache(Collections.singletonList(okxCoin));
        } catch (Exception e) {
            log.error("updateCoinTurnOver error:{}", e.getMessage());
        }
    }
}
