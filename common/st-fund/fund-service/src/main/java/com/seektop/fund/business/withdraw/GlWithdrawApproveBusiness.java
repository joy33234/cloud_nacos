package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlWithdrawApproveMapper;
import com.seektop.fund.model.GlWithdrawApprove;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

@Component
@Slf4j
public class GlWithdrawApproveBusiness extends AbstractBusiness<GlWithdrawApprove> {
    @Resource
    private GlWithdrawApproveMapper glWithdrawApproveMapper;

    public void saveWithdrawApprove(String orderId, Integer adminUserId, String adminUsername,
                                    Integer status, Integer withdrawType) {
        GlWithdrawApprove glWithdrawApprove = new GlWithdrawApprove();
        glWithdrawApprove.setOrderId(orderId);
        glWithdrawApprove.setUserId(adminUserId);
        glWithdrawApprove.setUsername(adminUsername);
        String remark = null;
        if (status == 1) {
            remark = "提现成功";
        } else {
            remark = "不处理";
        }
        glWithdrawApprove.setRemark(remark);
        glWithdrawApprove.setWithdrawType(withdrawType);
        glWithdrawApprove.setStatus(status);
        glWithdrawApprove.setCreateDate(new Date());
        glWithdrawApproveMapper.insert(glWithdrawApprove);
    }
}
