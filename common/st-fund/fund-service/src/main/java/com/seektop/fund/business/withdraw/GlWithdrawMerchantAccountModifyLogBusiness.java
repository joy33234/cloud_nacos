package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.mapper.GlWithdrawMerchantaccountModifyLogMapper;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.model.GlWithdrawMerchantaccountModifyLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

@Component
@Slf4j
public class GlWithdrawMerchantAccountModifyLogBusiness extends AbstractBusiness<GlWithdrawMerchantaccountModifyLog> {

    @Resource
    private GlWithdrawMerchantaccountModifyLogMapper glWithdrawMerchantaccountModifyLogMapper;


    public void saveModifyLog(GlWithdrawMerchantAccount account, GlAdminDO admin, Integer modifyType) throws GlobalException {

        if (account == null || admin == null || modifyType == null) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }

        GlWithdrawMerchantaccountModifyLog log = DtoUtils.transformBean(account, GlWithdrawMerchantaccountModifyLog.class);
        log.setCreator(admin.getUsername());
        log.setCreateDate(new Date());
        log.setModifyType(modifyType);
        if (modifyType != FundConstant.AccountModifyType.UPDATE_SCRIPT) {
            log.setScript(null);
        }
        glWithdrawMerchantaccountModifyLogMapper.insert(log);
    }
}
