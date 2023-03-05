package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;

import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.domain.OkxAccount;
import com.ruoyi.okx.enums.OrderStatusEnum;
import com.ruoyi.okx.mapper.BuyRecordMapper;
import com.ruoyi.okx.params.DO.BuyRecordDO;
import io.swagger.models.auth.In;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BuyRecordBusiness extends ServiceImpl<BuyRecordMapper, OkxBuyRecord> {
    private static final Logger log = LoggerFactory.getLogger(BuyRecordBusiness.class);

    @Resource
    private BuyRecordMapper buyRecordMapper;


    public List<OkxBuyRecord> selectList(BuyRecordDO buyRecordDO) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq((null != buyRecordDO.getCoin()), OkxBuyRecord::getCoin, buyRecordDO.getCoin());
        wrapper.between((buyRecordDO.getStartTime() != null), OkxBuyRecord::getUpdateTime, buyRecordDO.getStartTime(), buyRecordDO.getEndTime());
        return buyRecordMapper.selectList(wrapper);
    }

    public List<OkxBuyRecord> findSuccessRecord(String coin, Integer accountId, String modeType, Integer marketStatus) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.in(OkxBuyRecord::getStatus, Arrays.asList(new Integer[] { OrderStatusEnum.SUCCESS.getStatus(), OrderStatusEnum.PARTIALLYFILLED.getStatus() }));
        wrapper.eq((StringUtils.isNotEmpty(coin)), OkxBuyRecord::getCoin, coin);
        wrapper.eq((accountId != null), OkxBuyRecord::getAccountId, accountId);
        wrapper.eq((StringUtils.isNotEmpty(modeType)), OkxBuyRecord::getModeType, modeType);
        wrapper.eq((marketStatus != null), OkxBuyRecord::getMarketStatus, marketStatus);
        return this.buyRecordMapper.selectList(wrapper);
    }

    public boolean hasBuy(Integer accountId, Integer strategyId, String coin, Integer times) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxBuyRecord::getStrategyId, strategyId);
        wrapper.eq(OkxBuyRecord::getTimes, times);
        wrapper.eq(OkxBuyRecord::getAccountId, accountId);
        wrapper.eq(OkxBuyRecord::getCoin, coin);
        wrapper.in(OkxBuyRecord::getStatus, OrderStatusEnum.getUnFinishList());
        wrapper.ge(OkxBuyRecord::getCreateTime, DateUtil.parseSimpleDateTime("2000-1-1 00:00:00"));
        List<OkxBuyRecord> buyRecords = this.buyRecordMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(buyRecords)) {
            return false;
        }
        return true;
    }

    public List<OkxBuyRecord> findPendings(Integer accountId) {
        LambdaQueryWrapper<OkxBuyRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq(OkxBuyRecord::getStatus, OrderStatusEnum.PENDING.getStatus());
        if (accountId != null && accountId > 0) {
            wrapper.eq(OkxBuyRecord::getAccountId, accountId);
        }
        return this.buyRecordMapper.selectList(wrapper);
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
        return this.buyRecordMapper.selectList((Wrapper)wrapper);
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
}
