package com.seektop.fund.business.monitor;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.controller.backend.param.monitor.RechargePayerMonitorRecordListParamDO;
import com.seektop.fund.mapper.RechargePayerMonitorRecordMapper;
import com.seektop.fund.model.RechargePayerMonitorRecord;
import com.seektop.fund.vo.RechargePayerMonitorRecordListDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargePayerMonitorRecordBusiness extends AbstractBusiness<RechargePayerMonitorRecord> {

    private final RechargePayerMonitorRecordMapper rechargePayerMonitorRecordMapper;

    public Long getTipsCount() {
        return rechargePayerMonitorRecordMapper.getTipsCount();
    }

    public List<RechargePayerMonitorRecord> findByTimes(Integer times, int page, int size) {
        PageHelper.startPage(page, size);
        Condition condition = new Condition(RechargePayerMonitorRecord.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andLessThan("times", times);
        return findByCondition(condition);
    }

    public List<RechargePayerMonitorRecord> findByPayerName(String payerName, int page, int size) {
        PageHelper.startPage(page, size);
        Condition condition = new Condition(RechargePayerMonitorRecord.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andLike("payerName", "%" + payerName + "%");
        return findByCondition(condition);
    }

    public PageInfo<RechargePayerMonitorRecordListDO> findRecordList(RechargePayerMonitorRecordListParamDO paramDO) {
        PageHelper.startPage(paramDO.getPage(), paramDO.getSize());
        List<RechargePayerMonitorRecordListDO> recordList = rechargePayerMonitorRecordMapper.findRecordList(paramDO.getStartDate(), paramDO.getEndDate(), paramDO.getUsername());
        if (CollectionUtils.isEmpty(recordList)) {
            return new PageInfo<>(recordList);
        }
        for (RechargePayerMonitorRecordListDO recordListDO : recordList) {
            if (StringUtils.isEmpty(recordListDO.getPayerName())) {
                continue;
            }
            recordListDO.setPayers(JSON.parseArray(recordListDO.getPayerName()));
            recordListDO.setPayerName(null);
        }
        return new PageInfo<>(recordList);
    }

}