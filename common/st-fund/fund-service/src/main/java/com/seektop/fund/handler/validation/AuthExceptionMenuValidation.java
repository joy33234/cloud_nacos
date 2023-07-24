package com.seektop.fund.handler.validation;

import com.google.common.collect.Lists;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.system.service.GlSystemDepartmentJobService;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class AuthExceptionMenuValidation implements DataValidation {

    // 10030300 待审核
    // 10030301 审核搁置
    // 10030302 全部
    // 100303 风控提款审核

    private GlAdminDO admin;
    private Integer systemId;
    private GlSystemDepartmentJobService departmentJobService;

    @Override
    public void valid() throws GlobalException {
        Integer jobId = admin.getJobId();
        RPCResponse<List<Long>> response = departmentJobService.findMenuListByJobIdAndSystemId(jobId, systemId);
        List<Long> jobDataMenuList = RPCResponseUtils.getData(response);
        List<Long> menuIds = Lists.newArrayList(10030300L, 10030301L, 10030302L);
        boolean noneMatch = jobDataMenuList.stream().noneMatch(menuId -> menuIds.stream().anyMatch(id -> id.equals(menuId)));
        if(noneMatch) {
            throw new GlobalException("您没有数据操作权限");
        }
    }
}
