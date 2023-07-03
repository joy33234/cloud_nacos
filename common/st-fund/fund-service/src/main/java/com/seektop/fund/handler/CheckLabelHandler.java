package com.seektop.fund.handler;

import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawExceptionApproveDto;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.risk.dto.param.CheckLabelRecordDto;
import com.seektop.risk.service.CheckLabelService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Component
public class CheckLabelHandler {

    @DubboReference(timeout = 3000)
    private CheckLabelService checkLabelService;

    /**
     * 保存标记
     * @param withdraw
     * @param approveDto
     * @param admin
     */
    public void saveCheckLabel(GlWithdraw withdraw, WithdrawExceptionApproveDto approveDto, GlAdminDO admin) {
        Integer status = approveDto.getStatus();
        if (1 != status && 2 != status && 4 != status)
            return;
        List<Integer> labelIds = approveDto.getLabelIds();
        if (CollectionUtils.isEmpty(labelIds) || ObjectUtils.isEmpty(withdraw))
            return;
        CheckLabelRecordDto recordDto = new CheckLabelRecordDto();
        recordDto.setOrderId(withdraw.getOrderId());
        recordDto.setUserId(withdraw.getUserId());
        recordDto.setLabelIds(labelIds);
        recordDto.setUpdateUserId(admin.getUserId());
        recordDto.setUpdateUsername(admin.getUsername());
        checkLabelService.save(recordDto);
    }
}
