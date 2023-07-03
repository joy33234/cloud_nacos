package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawApproveDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawExceptionApproveDto;
import com.seektop.fund.mapper.GlWithdrawReturnRequestMapper;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawReturnRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class GlWithdrawReturnRequestBusiness extends AbstractBusiness<GlWithdrawReturnRequest> {
    @Resource
    private GlWithdrawReturnRequestMapper glWithdrawReturnRequestMapper;

    public List<String> findAllApprover() {
        return glWithdrawReturnRequestMapper.findAllApprover();
    }


    public List<String> findAllCreator() {
        return glWithdrawReturnRequestMapper.findAllCreator();
    }

    /**
     * 创建退回申请
     * @param withdraw
     * @param approveDto
     * @param admin
     */
    public void save(GlWithdraw withdraw, WithdrawExceptionApproveDto approveDto, GlAdminDO admin){
        //创建退回申请
        Date returnTime = new Date(approveDto.getUpdateTime().getTime() - 1000);
        GlWithdrawReturnRequest glWithdrawReturnRequest = new GlWithdrawReturnRequest();
        glWithdrawReturnRequest.setOrderId(withdraw.getOrderId());
        glWithdrawReturnRequest.setUserId(withdraw.getUserId());
        glWithdrawReturnRequest.setUserType(withdraw.getUserType());
        glWithdrawReturnRequest.setUsername(withdraw.getUsername());
        glWithdrawReturnRequest.setAmount(withdraw.getAmount());
        glWithdrawReturnRequest.setType(0);
        glWithdrawReturnRequest.setStatus(1);
        glWithdrawReturnRequest.setCreator(admin.getUsername());
        glWithdrawReturnRequest.setCreateTime(returnTime);
        glWithdrawReturnRequest.setApprover(admin.getUsername());
        glWithdrawReturnRequest.setApproveTime(returnTime);
        glWithdrawReturnRequest.setRemark(approveDto.getRemark());
        glWithdrawReturnRequest.setRejectReason(approveDto.getRejectReason());
        glWithdrawReturnRequest.setApproveRemark("风险拒绝出款-自动退回");
        glWithdrawReturnRequest.setWithdrawType(withdraw.getWithdrawType());
        glWithdrawReturnRequestMapper.insertSelective(glWithdrawReturnRequest);
    }

    /**
     * 提现拒绝 - 创建退回申请(审核通过)、提现金额退回中心余额、上报退回记录、
     * @param withdraw
     * @param approveDO
     * @param admin
     */
    public void save(GlWithdraw withdraw, WithdrawApproveDO approveDO, GlAdminDO admin){
        Date returnTime = new Date(approveDO.getUpdateTime().getTime() - 1000);
        GlWithdrawReturnRequest glWithdrawReturnRequest = new GlWithdrawReturnRequest();
        glWithdrawReturnRequest.setOrderId(withdraw.getOrderId());
        glWithdrawReturnRequest.setUserId(withdraw.getUserId());
        glWithdrawReturnRequest.setUserType(withdraw.getUserType());
        glWithdrawReturnRequest.setUsername(withdraw.getUsername());
        glWithdrawReturnRequest.setAmount(withdraw.getAmount());
        glWithdrawReturnRequest.setType(0);
        glWithdrawReturnRequest.setStatus(1);
        glWithdrawReturnRequest.setCreator(admin.getUsername());
        glWithdrawReturnRequest.setCreateTime(returnTime);
        glWithdrawReturnRequest.setApprover(admin.getUsername());
        glWithdrawReturnRequest.setApproveTime(returnTime);
        glWithdrawReturnRequest.setRemark(approveDO.getRemark());
        glWithdrawReturnRequest.setRejectReason(approveDO.getRejectReason());
        glWithdrawReturnRequest.setApproveRemark("拒绝处理-自动退回");
        glWithdrawReturnRequest.setWithdrawType(withdraw.getWithdrawType());
        glWithdrawReturnRequestMapper.insertSelective(glWithdrawReturnRequest);
    }

    /**
     * 验证是否已有审核记录（重复循环流程）
     * 存在 true  否则 false
     */
    public boolean checkExistApproveRecord(String orderId) {
        if (findById(orderId) != null) {
            return true;
        }
        return false;
    }
}
