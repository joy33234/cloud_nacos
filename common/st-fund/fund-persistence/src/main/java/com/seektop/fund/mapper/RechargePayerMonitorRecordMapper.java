package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.RechargePayerMonitorRecord;
import com.seektop.fund.vo.RechargePayerMonitorRecordListDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

public interface RechargePayerMonitorRecordMapper extends Mapper<RechargePayerMonitorRecord> {

    @Select("select case when count(*) is null then 0 else count(*) end as userCount from gl_recharge_payer_monitor_record")
    Long getTipsCount();

    List<RechargePayerMonitorRecordListDO> findRecordList(@Param(value = "startDate") Date startDate,
                                                          @Param(value = "endDate") Date endDate,
                                                          @Param(value = "username") String username);

}