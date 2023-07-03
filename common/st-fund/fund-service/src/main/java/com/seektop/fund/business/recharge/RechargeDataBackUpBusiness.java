package com.seektop.fund.business.recharge;

import com.seektop.common.utils.DateUtils;
import com.seektop.fund.mapper.GlRechargeBackUpMapper;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.GlRecharge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;

import java.util.Date;

@Slf4j
@Component
public class RechargeDataBackUpBusiness {

    @Autowired
    private GlRechargeMapper glRechargeMapper;

    @Autowired
    private GlRechargeBackUpMapper glRechargeBackUpMapper;


    public void backUp(Date backDate, String shard) {
        //查询GlRecharge 充值数据
        Condition condition = new Condition(GlRecharge.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andGreaterThanOrEqualTo("createDate", DateUtils.getMinTime(backDate));
        criteria.andLessThanOrEqualTo("createDate", DateUtils.getMaxTime(backDate));
        Integer totalCount = glRechargeMapper.selectCountByCondition(condition);
        log.info("充值数据备份 createDate = {}  count = {}", DateUtils.format(backDate, DateUtils.YYYY_MM_DD), totalCount);
        if (totalCount <= 0) {
            return;
        }
        int insertCount = glRechargeBackUpMapper.insert(shard, DateUtils.getMinTime(backDate), DateUtils.getMaxTime(backDate));
        log.info("充值数据备份插入 createDate = {}  count = {}", DateUtils.format(backDate, DateUtils.YYYY_MM_DD), insertCount);
    }

}
