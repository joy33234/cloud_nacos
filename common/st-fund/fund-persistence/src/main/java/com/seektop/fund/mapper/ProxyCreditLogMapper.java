package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.ProxyCreditLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProxyCreditLogMapper extends Mapper<ProxyCreditLog> {

	/**
	 * 更新状态
	 * @param status
	 * @param orderId
	 */
	@Update("update gl_proxy_credit_log set status = #{status} where order_id = #{orderId}")
	void updateStatus(@Param("status") Integer status, @Param("orderId") String orderId);

}