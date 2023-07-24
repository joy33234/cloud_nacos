package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.GlFundChangeRequest;
import com.seektop.fund.vo.FundsCheckReport;
import com.seektop.fund.vo.FundsExcelReport;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 资金调整申请
 */
public interface GlFundChangeRequestMapper extends Mapper<GlFundChangeRequest> {

    @Select("select distinct creator from gl_fund_changereq order by creator")
    List<String> findAllCreator();

    @Select("select distinct first_approver from gl_fund_changereq where first_approver is not null and first_approver !='' order by first_approver")
    List<String> findAllFirstApprover();

    @Select("select distinct second_approver from gl_fund_changereq where second_approver is not null and second_approver !='' order by second_approver")
    List<String> findAllSecondApprover();

    BigDecimal sumAllAmountByUserId(@Param("startDate") Date startDate, @Param("endDate") Date endDate,
                                    @Param("userId") Integer userId, @Param("changeType") Integer changeType,
                                    @Param("status") Integer status, @Param("subType") Integer subType);

    @Update("update gl_fund_changereq set status=#{status},first_approver=#{firstApprover},first_remark=#{remark},first_time=#{firstTime} where order_id in (${orderIds})")
    void updateList(@Param("orderIds") String orderIds,
                    @Param("status") Integer status,
                    @Param("firstApprover") String firstApprover,
                    @Param("remark") String remark,
                    @Param("firstTime") Date firstTime);

    List<GlFundChangeRequest> findByGlFundChangeRequest(
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("username") String username,
            @Param("userType") Integer userType,
            @Param("changeTypes") List<Integer> changeTypes,
            @Param("status") List<Integer> status,
            @Param("creator") String creator,
            @Param("firstApprover") String firstApprover,
            @Param("secondApprover") String secondApprover,
            @Param("subTypes") List<Integer> subTypes,
            @Param("orderId") String orderId,
            @Param("relationOrderId") String relationOrderId,
            @Param("dateType") Integer dateType);


    List<FundsCheckReport> findFundsCheckReportList(
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate,
            @Param("changeType") Integer changeType,
            @Param("subType") List<Integer> subType);

    List<FundsExcelReport> findFundsExcelReportList(
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate,
            @Param("changeType") Integer changeType,
            @Param("subType") List<Integer> subType,
            Integer page,
            Integer size);


    BigDecimal sumWrongRechargeTotal(@Param("startDate") Date startDate, @Param("endDate") Date endDate, @Param("userId") Integer userId);

}