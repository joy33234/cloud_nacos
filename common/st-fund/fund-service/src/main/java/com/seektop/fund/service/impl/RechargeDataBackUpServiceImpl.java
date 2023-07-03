package com.seektop.fund.service.impl;

import com.seektop.common.utils.DateUtils;
import com.seektop.fund.business.recharge.RechargeDataBackUpBusiness;
import com.seektop.fund.mapper.GlRechargeBackUpMapper;
import com.seektop.fund.payment.yixunpay.StringUtils;
import com.seektop.fund.service.RechargeDataBackUpService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;

@Slf4j
@DubboService(timeout = 5000, interfaceClass = RechargeDataBackUpService.class)
public class RechargeDataBackUpServiceImpl implements RechargeDataBackUpService {

    @Value("${fund.table.schema}")
    private String tableSchema;

    @Autowired
    private GlRechargeBackUpMapper glRechargeBackUpMapper;

    @Autowired
    private RechargeDataBackUpBusiness rechargeDataBackUpBusiness;


    @Override
    public String getMaxRechargeDate(String shard) {
        if (checkTable(shard)) {
            return glRechargeBackUpMapper.getMaxRechargeDate(shard);
        }
        return null;
    }

    @Override
    public void backUp(Date backDate) {

        //每次处理一天的数据
        int diffDay = DateUtils.diffDay(backDate, new Date());
        log.info("充值数据备份日期天数  diffDay  = {}", diffDay);
        for (int i = 0; i < diffDay; i++) {
            Integer shard = DateUtils.getYear(backDate);
            if (!checkTable(shard.toString())) {
                glRechargeBackUpMapper.createTable(shard.toString());
            }
            rechargeDataBackUpBusiness.backUp(backDate, shard.toString());
            
            backDate = DateUtils.addDay(1, backDate);
        }


    }

    @Override
    public Integer clean() {
        return null;
    }

    @Override
    public boolean checkTable(String shard) {
        String tableName = glRechargeBackUpMapper.checkTable(shard, tableSchema);
        if (StringUtils.isEmpty(tableName) || !tableName.equals(String.format("gl_recharge_backup_%s", shard))) {
            return false;
        }
        return true;
    }
}
