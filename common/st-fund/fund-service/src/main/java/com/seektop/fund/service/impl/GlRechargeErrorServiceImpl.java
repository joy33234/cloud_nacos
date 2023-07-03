package com.seektop.fund.service.impl;

import com.seektop.fund.business.recharge.GlRechargeErrorBusiness;
import com.seektop.fund.service.GlRechargeErrorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.Date;


/**
 * 删除充值异常记录(保留7天)
 */

@Slf4j
@DubboService(retries = 2, timeout = 3000, interfaceClass = GlRechargeErrorService.class)
public class GlRechargeErrorServiceImpl implements GlRechargeErrorService {

    @Resource
    private GlRechargeErrorBusiness glRechargeErrorBusiness;


    @Override
    public void deleteRecord(Date date) {
        glRechargeErrorBusiness.deleteRecord(date);
    }

}
