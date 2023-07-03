package com.seektop.fund.business;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlFundReportRecordMapper;
import com.seektop.fund.model.GlFundReportRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.Date;

@Component
@Slf4j
public class GlFundReportRecordBusiness extends AbstractBusiness<GlFundReportRecord> {
    @Resource
    private GlFundReportRecordMapper glFundReportRecordMapper;


    public void deleteLogs(Date date){
        Condition condition = new Condition(GlFundReportRecord.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("status", 1);
        criteria.andLessThan("lastupdate", date);
        int count = glFundReportRecordMapper.deleteByCondition(condition);
        log.info("delete success date = {}, count = {}", date, count);
    }
}
