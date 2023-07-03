package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.C2CMatchLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

public interface C2CMatchLogMapper extends Mapper<C2CMatchLog> {

    @Select("select create_date from gl_c2c_match_log where order_id = #{orderId} and type = #{type} order by create_date desc limit 1")
    Date getLogCreateDate(@Param(value = "orderId") String orderId,
                          @Param(value = "type") Short type);

    @Select("select create_date from gl_c2c_match_log where order_id = #{orderId} and linked_order_id = #{linkedOrderId} and type = #{type} order by create_date desc limit 1")
    Date getLogCreateDateWithLinkedOrderId(@Param(value = "orderId") String orderId,
                                           @Param(value = "linkedOrderId") String linkedOrderId,
                                           @Param(value = "type") Short type);

}