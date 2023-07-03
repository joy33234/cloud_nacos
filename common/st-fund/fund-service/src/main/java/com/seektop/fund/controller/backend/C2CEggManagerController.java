package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.param.c2c.C2CEggOpenParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CEggRecordListParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CEggStopParamDO;
import com.seektop.fund.handler.C2CEggRecordHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/manage/fund/c2c/egg")
public class C2CEggManagerController extends FundBackendBaseController {

    @Resource
    private C2CEggRecordHandler c2CEggRecordHandler;

    /**
     * 彩蛋记录-列表
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/record/list")
    public Result listRecord(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated C2CEggRecordListParamDO paramDO) {
        return c2CEggRecordHandler.listRecord(adminDO, paramDO);
    }

    /**
     * 开启彩蛋
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/submit/open")
    public Result submitOpen(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated C2CEggOpenParamDO paramDO) {
        return c2CEggRecordHandler.submitOpen(adminDO, paramDO);
    }

    /**
     * 停止彩蛋
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/submit/stop")
    public Result submitStop(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated C2CEggStopParamDO paramDO) {
        return c2CEggRecordHandler.submitStop(adminDO, paramDO);
    }

    /**
     * 彩蛋信息
     *
     * @param adminDO
     * @return
     */
    @PostMapping(value = "/load/info")
    public Result loadC2CEggResult(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO) {
        return c2CEggRecordHandler.loadC2CEggResult(adminDO);
    }

}