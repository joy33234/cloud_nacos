package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.param.c2c.C2CMatchLogListParamDO;
import com.seektop.fund.handler.C2COrderHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/manage/fund/c2c/order")
public class C2COrderManagerController extends FundBackendBaseController {

    @Resource
    private C2COrderHandler c2COrderHandler;

    /**
     * 撮合日志-列表
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/match/log/list")
    public Result matchLogList(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated C2CMatchLogListParamDO paramDO) {
        return c2COrderHandler.matchLogList(adminDO, paramDO);
    }

}