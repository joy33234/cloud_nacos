package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawExceptionApproveDto;
import com.seektop.fund.mapper.GlWithdrawRiskApproveMapper;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawRiskApprove;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class WithdrawRiskApproveBusiness extends AbstractBusiness<GlWithdrawRiskApprove> {

    @Resource
    private GlWithdrawRiskApproveMapper withdrawRiskApproveMapper;

    public List<GlWithdrawRiskApprove> findByOrderIds(List<String> orderIds){
        if(CollectionUtils.isEmpty(orderIds)) {
            return Lists.newArrayList();
        }
        String join = StringUtils.join(orderIds.stream().distinct().collect(Collectors.toList()), "','");
        return findByIds(String.format("'%s'", join));
    }

    /**
     * 保存提现风控审核记录
     * @param withdraw
     * @param approveOptional
     * @param approveDto
     * @param admin
     */
    public void save(GlWithdraw withdraw, Optional<GlWithdrawRiskApprove> approveOptional,
                     WithdrawExceptionApproveDto approveDto, GlAdminDO admin){
        Integer status = approveDto.getStatus();
        if (!approveOptional.isPresent()) {
            GlWithdrawRiskApprove approve = new GlWithdrawRiskApprove();
            approve.setCreateDate(approveDto.getUpdateTime());
            approve.setOrderId(withdraw.getOrderId());
            approve.setRemark(approveDto.getRemark());
            approve.setStatus(status == 1 ? 1 : status == 3 ? 3 : 2);
            approve.setUserId(admin.getUserId());
            approve.setUsername(admin.getUsername());
            approve.setRejectReason(approveDto.getRejectReason());
            withdrawRiskApproveMapper.insertSelective(approve);
        } else {
            GlWithdrawRiskApprove approve = approveOptional.get();
            approve.setRemark(approveDto.getRemark());
            approve.setRejectReason(approveDto.getRejectReason());
            approve.setStatus(status == 1 ? 1 : status == 3 ? 3 : 2);
            withdrawRiskApproveMapper.updateByPrimaryKey(approve);
        }
    }
}
