package com.ruoyi.okx.business;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.redis.service.RedisLock;
import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.domain.OkxSellRecord;
import com.ruoyi.okx.enums.OrderStatusEnum;
import com.ruoyi.okx.mapper.SellRecordMapper;
import com.ruoyi.okx.params.DO.SellRecordDO;
import com.ruoyi.okx.utils.Constant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SellRecordBusiness extends ServiceImpl<SellRecordMapper, OkxSellRecord> {
    private static final Logger log = LoggerFactory.getLogger(SellRecordBusiness.class);

    @Resource
    private SellRecordMapper sellRecordMapper;

    @Resource
    private BuyRecordBusiness buyRecordBusiness;

    @Resource
    private CommonBusiness commonBusiness;

    @Resource
    private AccountBalanceBusiness balanceBusiness;

    @Resource
    private CoinProfitBusiness coinProfitBusiness;

    @Autowired
    private RedisLock redisLock;


    public List<OkxSellRecord> selectList(SellRecordDO sellRecordDO) {
        LambdaQueryWrapper<OkxSellRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq((null != sellRecordDO.getCoin()), OkxSellRecord::getCoin, sellRecordDO.getCoin());
        wrapper.eq((null != sellRecordDO.getAccountName()), OkxSellRecord::getAccountName, sellRecordDO.getAccountName());
        wrapper.eq((null != sellRecordDO.getStatus()), OkxSellRecord::getStatus, sellRecordDO.getStatus());
        wrapper.between((sellRecordDO.getParams().get("beginTime") != null), OkxSellRecord::getCreateTime, sellRecordDO.getParams().get("beginTime"), sellRecordDO.getParams().get("endTime"));
        wrapper.orderByDesc(OkxSellRecord::getUpdateTime);
        return sellRecordMapper.selectList( wrapper);
    }

    public List<OkxSellRecord> findPendings(Integer accountId) {
        LambdaQueryWrapper<OkxSellRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxSellRecord::getStatus, OrderStatusEnum.PENDING.getStatus());
        wrapper.eq(OkxSellRecord::getAccountId, accountId);
        return sellRecordMapper.selectList((Wrapper)wrapper);
    }


    public boolean update(OkxSellRecord record) {
        return sellRecordMapper.updateById(record) > 0 ? true : false;
    }


    @Transactional(rollbackFor = {Exception.class})
    public void syncSellOrderStatus(Map<String, String> map) {
        List<OkxSellRecord> list = findPendings(Integer.valueOf(map.get("id")));
        Date now = new Date();
        String lockKey = "";
        for (OkxSellRecord sellRecord:list) {
            lockKey = RedisConstants.OKX_SYNC_SELL_ORDER + sellRecord.getId();
            boolean lock = redisLock.lock(lockKey,30,3,2000);
            if (lock == false) {
                log.error("tradeV2获取锁失败，交易取消 lockKey:{}",lockKey);
                continue;
            }
            String str = HttpUtil.getOkx("/api/v5/trade/order?instId=" + sellRecord.getInstId() + "&ordId=" + sellRecord.getOkxOrderId(), null, map);
            JSONObject json = JSONObject.parseObject(str);
            if (json == null || !json.getString("code").equals("0")) {
                log.error("获取卖出订单信息异常：{}", (json == null) ? "null" : json.toJSONString());
                return;
            }
            JSONObject data = json.getJSONArray("data").getJSONObject(0);
            sellRecord.setStatus((commonBusiness.getOrderStatus(data.getString("state")) == null) ? sellRecord.getStatus() : commonBusiness.getOrderStatus(data.getString("state")));
            if (sellRecord.getStatus().equals(OrderStatusEnum.SUCCESS.getStatus())) {
                OkxBuyRecord okxBuyRecord = buyRecordBusiness.findOne(sellRecord.getBuyRecordId());
                sellRecord.setFee(data.getBigDecimal("fee").setScale(Constant.OKX_BIG_DECIMAL, RoundingMode.HALF_UP).abs());
                sellRecord.setProfit(sellRecord.getAmount().subtract(sellRecord.getFee()).subtract(okxBuyRecord.getAmount()).subtract(okxBuyRecord.getFeeUsdt()));
                boolean update = updateById(sellRecord);
                if (update) {
                    balanceBusiness.reduceCount(sellRecord.getCoin(), sellRecord.getAccountId(), sellRecord.getQuantity());
                    okxBuyRecord.setStatus(OrderStatusEnum.FINISH.getStatus());
                    buyRecordBusiness.update(okxBuyRecord);
                    coinProfitBusiness.calculateProfit(sellRecord);
                    return;
                }
            }
            if (sellRecord.getStatus().equals(OrderStatusEnum.FAIL.getStatus())) {
                Integer canBuuRecordId = Integer.valueOf(RandomUtil.randomInt(1000000000));
                sellRecord.setBuyRecordId(Integer.valueOf(-canBuuRecordId.intValue()));
                boolean update = updateById(sellRecord);
                if (update) {
                    OkxBuyRecord buyRecord = this.buyRecordBusiness.getById(sellRecord.getBuyRecordId());
                    if (buyRecord == null) {
                        log.error("syncSellOrderStatus 对应买入订单不存在:{}", sellRecord.getBuyRecordId());
                        return;
                    }
                }
            }
            if (sellRecord.getStatus().equals(OrderStatusEnum.PENDING.getStatus())) {
                int diffDays = DateUtil.diffDay(DateUtil.getMinTime(sellRecord.getCreateTime()), DateUtil.getMinTime(now));
                if (diffDays > 0) {
                    Map<String, String> params = new HashMap<>(8);
                    params.put("instId", sellRecord.getInstId());
                    params.put("ordId", sellRecord.getOkxOrderId());
                    String cancelStr = HttpUtil.postOkx("/api/v5/trade/cancel-order", params, map);
                    JSONObject cancelJson = JSONObject.parseObject(cancelStr);
                    if (cancelJson == null || !cancelJson.getString("code").equals("0")) {
                        log.error("{}", JSON.toJSONString(params));
                        return;
                    }
                    JSONObject dataJSON = cancelJson.getJSONArray("data").getJSONObject(0);
                    if (dataJSON == null || !dataJSON.getString("sCode").equals("0")) {
                        log.error("{}", JSON.toJSONString(params));
                        return;
                    }
                    OkxBuyRecord buyRecord = this.buyRecordBusiness.getById(sellRecord.getBuyRecordId());
                    if (buyRecord == null) {
                        log.error("查询买入订单异常:{}", sellRecord.getBuyRecordId());
                        return;
                    }
                    sellRecord.setStatus(OrderStatusEnum.CANCEL.getStatus());
                    Integer canBuuRecordId = Integer.valueOf(RandomUtil.randomInt(1000000000));
                    sellRecord.setBuyRecordId(Integer.valueOf(-canBuuRecordId.intValue()));
                    updateById(sellRecord);
                    buyRecord.setStatus(OrderStatusEnum.SUCCESS.getStatus());
                    this.buyRecordBusiness.updateById(buyRecord);
                    log.info("订单买入超过1天自动撤销");
                }
            }
            redisLock.releaseLock(lockKey);
        }
        list.stream().forEach(sellRecord -> {

        });
    }
}

