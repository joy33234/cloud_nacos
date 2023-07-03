package com.seektop.fund.business.recharge;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.mapper.GlPaymentMerchantaccountModifyLogMapper;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlPaymentMerchantaccountModifyLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

@Component
@Slf4j
public class GlPaymentMerchantAccountModifyLogBusiness extends AbstractBusiness<GlPaymentMerchantaccountModifyLog> {

    @Resource
    private GlPaymentMerchantaccountModifyLogMapper glPaymentMerchantaccountModifyLogMapper;



    public void saveModifyLog(GlPaymentMerchantaccount account, GlAdminDO admin, Integer modifyType) throws GlobalException {

        if (account == null || admin == null || modifyType == null) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }

        GlPaymentMerchantaccountModifyLog log = DtoUtils.transformBean(account, GlPaymentMerchantaccountModifyLog.class);
        log.setCreator(admin.getUsername());
        log.setCreateDate(new Date());
        log.setModifyType(modifyType);
        if (modifyType != FundConstant.AccountModifyType.UPDATE_SCRIPT) {
            log.setScript(null);
        }
        glPaymentMerchantaccountModifyLogMapper.insert(log);
    }


}
