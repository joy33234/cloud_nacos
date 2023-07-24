package com.seektop.fund.controller.partner;

import com.seektop.common.rest.Result;
import com.seektop.common.utils.HttpUtils;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlWithdrawRecordBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.controller.backend.param.withdraw.WithdrawAlarmDto;
import com.seektop.fund.controller.backend.result.withdraw.GlWithdrawCollectResult;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawConfirmDto;
import com.seektop.fund.controller.partner.param.TimeOrderQueryForm;
import com.seektop.fund.handler.WithdrawHandler;
import com.seektop.fund.model.GlWithdrawAlarm;
import com.seektop.fund.payment.WithdrawNotifyResponse;
import com.seektop.fund.vo.GlWithdrawQueryDto;
import com.seektop.fund.vo.WithdrawVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.List;

/**
 * 提现相关外部接口
 * 需要设置IP白名单
 */
@Slf4j
@RestController
@RequestMapping("/partner/fund/withdraw")
public class PartnerWithdrawController extends GlFundPartnerBaseController {

    @Resource(name = "withdrawHandler")
    private WithdrawHandler withdrawHandler;

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;

    @Resource
    private GlWithdrawRecordBusiness glWithdrawRecordBusiness;

    /**
     * 提现订单回调接收接口
     */
    @RequestMapping(value = "/notify/{merchantId}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseStatus(HttpStatus.OK)
    public void notify(@PathVariable(value = "merchantId") Integer merchantId, HttpServletRequest request,
                       HttpServletResponse response) {
        try {
            PrintWriter out = response.getWriter();
            WithdrawNotifyResponse notify = withdrawHandler.withdrawNotify(merchantId, request, response);
            out.write(notify.getContent());
            out.flush();
        } catch (Exception e) {
            log.error("partner withdraw notify err", e);
            return;
        }
    }

    /**
     * 提现资金校验接口 - TGPay
     *
     * @param merchantId
     * @param request
     * @param response
     */
    @RequestMapping(value = "/notify/{merchantId}/verify", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseStatus(HttpStatus.OK)
    public void withdrawVerify(@PathVariable(value = "merchantId") Integer merchantId, HttpServletRequest request,
                               HttpServletResponse response) {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            WithdrawNotifyResponse notify = withdrawHandler.notifyForStormPay(merchantId, request);
            out.write(notify.getContent());
            out.flush();
            return;
        } catch (Exception e) {
            log.error("partner withdraw verify err", e);
            return;
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

    /**
     * 提现订单回调接收接口 - 风云聚合专用
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
            WithdrawNotifyResponse notify = withdrawHandler.notifyForStormPay(merchantId, request);
            out.write(notify.getContent());
            out.flush();
            return;
        } catch (Exception e) {
            log.error("partner notify stormPay err", e);
            return;
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }


    /**
     * 风云聚合插件查询出款订单是否存在
     *
     * @return
     * @throws GlobalException
     */
    @RequestMapping(value = "/confirm", method = {RequestMethod.POST, RequestMethod.GET})
    public Result confirm(@Validated WithdrawConfirmDto confirmDto) {
        Boolean result = withdrawHandler.withdrawConfirm(confirmDto);
        log.info("{}订单验证结果:{}", confirmDto.getOrderId(), result);
        return Result.genSuccessResult(result);
    }


    /**
     * 异常提现运维告警：数据接口
     *
     * @param alarmDto
     * @param request
     * @return
     * @throws ParseException
     */
    @RequestMapping(value = "/alarm", method = {RequestMethod.POST, RequestMethod.GET})
    public Result alarm(@Validated WithdrawAlarmDto alarmDto,
                        HttpServletRequest request) throws GlobalException {
        String ip = HttpUtils.getRequestIp(request);
        alarmDto.setIp(ip);
        List<GlWithdrawAlarm> result = glWithdrawBusiness.withdrawAlarms(alarmDto);
        return Result.genSuccessResult(result);
    }

    /**
     * 提现订单记录列表
     */
    @PostMapping(value = "/record", produces = "application/json;charset=utf-8")
    public Result record(TimeOrderQueryForm dto) throws GlobalException {
        GlWithdrawQueryDto queryDto = new GlWithdrawQueryDto();
        queryDto.setUserId(dto.getUserId());
        queryDto.setPage(dto.getPage());
        queryDto.setSize(dto.getSize());
        queryDto.setOrderId(dto.getOrderId());
        if (dto.getStatus()!=null) queryDto.setWithdrawStatus(dto.getStatus());
        queryDto.setStartTime(dto.getStime());
        queryDto.setEndTime(dto.getEtime());
        GlWithdrawCollectResult<WithdrawVO> pageInfo = glWithdrawRecordBusiness.getWithdrawHistoryPageList(queryDto);
        return Result.genSuccessResult(pageInfo);
    }
}
