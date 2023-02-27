package com.ruoyi.okx.business;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;

import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.okx.domain.OkxBuyRecord;
import com.ruoyi.okx.domain.OkxSellRecord;
import com.ruoyi.okx.domain.OkxAccount;
import com.ruoyi.okx.enums.OrderStatusEnum;
import com.ruoyi.okx.mapper.SellRecordMapper;
import com.ruoyi.okx.params.DO.SellRecordDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SellRecordBusiness extends ServiceImpl<SellRecordMapper, OkxSellRecord> {
    private static final Logger log = LoggerFactory.getLogger(SellRecordBusiness.class);

    @Resource
    private SellRecordMapper sellRecordMapper;

    public List<OkxSellRecord> selectList(SellRecordDO sellRecordDO) {
        LambdaQueryWrapper<OkxSellRecord> wrapper = new LambdaQueryWrapper();
        wrapper.eq((null != sellRecordDO.getCoin()), OkxSellRecord::getCoin, sellRecordDO.getCoin());
        wrapper.between((sellRecordDO.getStartTime() != null), OkxSellRecord::getUpdateTime, sellRecordDO.getStartTime(), sellRecordDO.getEndTime());
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
}

