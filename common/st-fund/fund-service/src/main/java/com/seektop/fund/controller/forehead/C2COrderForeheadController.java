package com.seektop.fund.controller.forehead;

import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlUserDO;
import com.seektop.fund.handler.C2CEggRecordHandler;
import com.seektop.fund.handler.C2COrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping(value = "/forehead/fund/c2c")
public class C2COrderForeheadController extends FundForeheadBaseController {

    @Resource
    private C2COrderHandler c2COrderHandler;
    @Resource
    private C2CEggRecordHandler c2CEggRecordHandler;

    /**
     * 获取彩蛋信息
     *
     * @param userDO
     * @return
     */
    @PostMapping(value = "/get/egg/info")
    public Result loadC2CEggResult(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, @RequestParam String coin) {
        return c2CEggRecordHandler.loadC2CEggResult(userDO, coin);
    }

    /**
     * 充值订单-确认付款
     *
     * @param userDO
     * @param orderId
     * @return
     */
    @PostMapping(value = "/submit/confirm/payment")
    public Result submitConfirmPayment(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, String orderId, ParamBaseDO paramBaseDO) {
        return c2COrderHandler.submitConfirmPayment(orderId, userDO,paramBaseDO);
    }

    /**
     * 提现订单-确认到账
     *
     * @param userDO
     * @param orderId
     * @return
     */
    @PostMapping(value = "/submit/confirm/receive")
    public Result submitConfirmReceive(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, String orderId, ParamBaseDO paramBaseDO) {
        return c2COrderHandler.submitConfirmReceive(orderId, userDO, paramBaseDO);
    }

}