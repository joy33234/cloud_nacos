package com.seektop.fund.controller.partner;

import com.seektop.common.rest.Result;
import com.seektop.common.utils.HttpUtils;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.controller.backend.param.recharge.account.MerchantAccountMonitorDO;
import com.seektop.fund.controller.backend.result.recharge.GlRechargeCollectResult;
import com.seektop.fund.controller.backend.result.recharge.RechargeMonitorRetResult;
import com.seektop.fund.controller.partner.param.TimeOrderQueryForm;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.handler.RechargeHandler;
import com.seektop.fund.handler.RechargeRecordHandler;
import com.seektop.fund.payment.RechargeNotifyResponse;
import com.seektop.fund.vo.RechargeQueryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.List;

/**
 * 充值相关外部接口
 * 需要设置IP白名单
 */
@Slf4j
@RestController
@RequestMapping("/partner/fund/recharge")
public class PartnerRechargeController extends GlFundPartnerBaseController {

    @Resource(name = "rechargeHandler")
    private RechargeHandler rechargeHandler;

    @Resource
    private RechargeRecordHandler rechargeRecordHandler;

    @Resource
    private GlRechargeBusiness glRechargeBusiness;

    /**
     * 充值订单回调接口-通用渠道
     *
     * @param merchantId
     * @param request
     * @param response
     */
    @RequestMapping(value = "/notify/{merchantId}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseStatus(HttpStatus.OK)
    public void notify(@PathVariable(value = "merchantId") Integer merchantId, HttpServletRequest request,
                       HttpServletResponse response) {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            RechargeNotifyResponse notify = rechargeHandler.notify(merchantId, request);
            out.write(notify.getContent());
            out.flush();
            return;
        } catch (Exception e) {
            log.error("partner recharge notify err", e);
            return;
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }


    /**
     * 充值订单回调接口-StormPay专用
     *
     * @param merchantId
     * @param request
     * @param response
     */
    @RequestMapping(value = "/notify/storm/{merchantId}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseStatus(HttpStatus.OK)
    public void notifyForStormPay(@PathVariable(value = "merchantId") Integer merchantId, HttpServletRequest request,
                                  HttpServletResponse response) {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            RechargeNotifyResponse notify = rechargeHandler.notifyForStormPay(merchantId, request);
            out.write(notify.getContent());
            out.flush();
            return;
        } catch (Exception e) {
            log.error("notify for stormPay err", e);
            return;
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

    @RequestMapping(value = "/status/monitor", method = {RequestMethod.POST, RequestMethod.GET})
    public Result rechargeMonitor(@Validated MerchantAccountMonitorDO monitorDO,
                                  HttpServletRequest request) throws GlobalException {
        String ip = HttpUtils.getRequestIp(request);
        monitorDO.setIp(ip);
        List<RechargeMonitorRetResult> result = glRechargeBusiness.rechargeMonitor(monitorDO);
        return Result.genSuccessResult(result);
    }

    @RequestMapping(value = "/record", method = {RequestMethod.POST, RequestMethod.GET})
    public Result rechargeRecord(@Validated TimeOrderQueryForm dto) throws GlobalException {
        RechargeQueryDto queryDto = new RechargeQueryDto();
        queryDto.setUserId(dto.getUserId());
        queryDto.setOrderId(dto.getOrderId());
        queryDto.setEndTime(dto.getEtime());
        queryDto.setStartTime(dto.getStime());
        queryDto.setPage(dto.getPage());
        queryDto.setSize(dto.getSize());
        queryDto.setMainStatus(dto.getMainStatus());
        if (dto.getStatus()!=null) queryDto.setOrderStatus(dto.getStatus());
        if (dto.getPaymentId()!=null) queryDto.setPaymentIdList(dto.getPaymentId());
        GlRechargeCollectResult<GlRechargeDO> rechargeRecordPageList = rechargeRecordHandler.findRechargeRecordPageList(queryDto);
        return Result.genSuccessResult(rechargeRecordPageList);
    }
}
