package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlRechargeSuccessApprove;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 充值补单审核
 */
public interface GlRechargeSuccessApproveMapper extends Mapper<GlRechargeSuccessApprove> {

    @Select("select distinct username from gl_recharge_sucapv")
    List<String> findAllAuditor();

    String getTotalAmount(@Param("ids") List<String> ids);
}