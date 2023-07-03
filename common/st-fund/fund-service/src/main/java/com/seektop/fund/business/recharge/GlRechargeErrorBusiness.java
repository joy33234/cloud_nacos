package com.seektop.fund.business.recharge;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.recharge.RechargeErrorDO;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.mapper.GlRechargeErrorMapper;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlRechargeError;
import com.seektop.fund.payment.GlRechargeResult;
import com.seektop.fund.vo.RechargeMonitorResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class GlRechargeErrorBusiness extends AbstractBusiness<GlRechargeError> {

    @Autowired
    private GlRechargeErrorMapper glRechargeErrorMapper;


    public PageInfo<GlRechargeError> findPageList(RechargeErrorDO dto) {

        PageHelper.startPage(dto.getPage(), dto.getSize());
        Condition con = new Condition(GlRechargeError.class);
        Example.Criteria criteria = con.createCriteria();
        if (dto.getStartTime() != null) {
            criteria.andGreaterThanOrEqualTo("rechargeDate", dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            criteria.andLessThanOrEqualTo("rechargeDate", dto.getEndTime());
        }
        if (dto.getUserType() != -1) {
            criteria.andEqualTo("userType", dto.getUserType());
        }
        if (StringUtils.isNotEmpty(dto.getUserName())) {
            criteria.andEqualTo("username", dto.getUserName());
        }
        if (dto.getChannelId() != -1) {
            criteria.andEqualTo("channelId", dto.getChannelId());
        }
        if (StringUtils.isNotEmpty(dto.getMerchantCode())) {
            criteria.andEqualTo("merchantCode", dto.getMerchantCode());
        }
        if (dto.getErrorStatus() != -1) {
            criteria.andEqualTo("errorStatus", dto.getErrorStatus());
        }
        con.setOrderByClause("recharge_date desc");
        List<GlRechargeError> list = glRechargeErrorMapper.selectByCondition(con);
        return new PageInfo(list);
    }

    public void save(GlUserDO userDO, Integer osType, Date now, GlPaymentMerchantApp merchantApp, GlRechargeResult result){

        GlRechargeError error = new GlRechargeError();
        error.setUserType(userDO.getUserType());
        error.setUsername(userDO.getUsername());
        error.setOsType(osType);
        if (result.getErrorCode() == FundConstant.RechargeErrorCode.PAYMENT) {
            error.setErrorStatus(FundConstant.RechargeErrorStatus.PAYMENT);
        } else if (result.getErrorCode() == FundConstant.RechargeErrorCode.SYSTEM) {
            error.setErrorStatus(FundConstant.RechargeErrorStatus.SYSTEM);
        }
        error.setRechargeDate(now);
        error.setChannelId(merchantApp.getChannelId());
        error.setChannelName(merchantApp.getChannelName());
        error.setMerchantCode(merchantApp.getMerchantCode());
        error.setErrorMsg(result.getErrorMsg());
        error.setOrderId(result.getTradeId());
        glRechargeErrorMapper.insertSelective(error);
    }


    public void deleteRecord(Date date){
        Condition condition = new Condition(GlRechargeError.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andLessThan("rechargeDate", date);
        int count = glRechargeErrorMapper.deleteByCondition(condition);
        log.info("delete_success_date = {}, count = {}", date, count);
    }


    public List<RechargeMonitorResult> getRecentHundredErrorOrder(Integer channelId, Date startDate) {
        return glRechargeErrorMapper.getRecentHundredErrorOrder(channelId, startDate);
    }

}
