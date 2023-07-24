package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlRechargeError;
import com.seektop.fund.vo.RechargeMonitorResult;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 充值异常记录
 */
public interface GlRechargeErrorMapper extends Mapper<GlRechargeError> {

    List<RechargeMonitorResult> getRecentHundredErrorOrder(@Param("channelId") Integer channelId, @Param("startDate") Date startDate);

}