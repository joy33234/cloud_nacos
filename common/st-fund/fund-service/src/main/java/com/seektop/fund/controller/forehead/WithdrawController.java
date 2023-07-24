package com.seektop.fund.controller.forehead;

import com.seektop.common.redis.RedisLock;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.backend.dto.withdraw.RejectWithdrawRequestDO;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawConfirmDto;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawSubmitDO;
import com.seektop.fund.controller.forehead.result.GlWithdrawDetailResult;
import com.seektop.fund.controller.forehead.result.WithdrawResult;
import com.seektop.fund.handler.WithdrawHandler;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.payment.WithdrawNotifyResponse;
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
 * Created by CodeGenerator on 2018/03/18.
 */
@Slf4j
@RestController
@RequestMapping("/forehead/fund/withdraw")
public class WithdrawController extends GlFundForeheadBaseController {

    @Resource
    private RedisLock redisLock;

    @Resource(name = "withdrawHandler")
    private WithdrawHandler withdrawHandler;

    @Resource
    private RedisService redisService;

    /**
     * 获取提现配置信息
     *
     * @param userDO
     * @return
     */
    @PostMapping("/info")
    public Result info(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, String coin) {
        return withdrawHandler.loadWithdrawInfo(userDO, coin);
    }

    /**
     * 获取提现配置信息
     *
     * @param userDO
     * @return
     */
    @PostMapping("/info/new")
    public Result infoNew(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, @RequestParam String coin) {
        return withdrawHandler.loadWithdrawInfoNew(userDO, coin);
    }

    /**
     * 提现接口是否开启
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    @PostMapping("/isClosed")
    public Result isClosed(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) throws GlobalException {
        RejectWithdrawRequestDO result = withdrawHandler.isClosed(userDO);
        if (null == result) {
            return Result.genSuccessResult();
        }
        return Result.genFailResult(ResultCode.WITHDRAWAL_CLOSED.getCode(), result.getContent(), result);
    }

    /**
     * 虚拟币提现USDT汇率查询
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping("/usdt/rate")
    public Result usdtWithdrawRate(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) {
        return withdrawHandler.loadUsdtRate(userDO);
    }

    /**
     * 游客查看提现配置信息(提现说明)
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping("/outlogin/info")
    public Result outloginInfo() throws GlobalException {
        return Result.genSuccessResult(withdrawHandler.withdrawInfoForVisitor());
    }

    /**
     * 提现订单发起
     *
     * @param withdrawSubmitDto
     * @param userDO
     * @param request
     * @return
     * @throws GlobalException
     */
    @PostMapping("/submit")
    public Result submit(@Validated WithdrawSubmitDO withdrawSubmitDto, @ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, HttpServletRequest request) {
        // 按用户ID进行并发锁操作，防止重复提交。
        String lockKey = "USER_WITHDRAW_SUBMIT_" + userDO.getId();
        try {
            redisLock.lock(lockKey, 20, 100, 195);
            List<GlWithdraw> withdrawList = withdrawHandler.doWithdrawSubmit(withdrawSubmitDto, userDO, request);
            return Result.genSuccessResult(withdrawList);
        } catch (GlobalException e) {
            log.error("WithdrawController_submit_err = {}", e.getExtraMessage());
            return Result.genFailResult(ResultCode.WITHDRAW_SUBMIT_ERROR.getCode(), e.getExtraMessage());
        } finally {
            redisLock.releaseLock(lockKey);
        }
    }

    /**
     * 获取提现订单详情
     *
     * @param orderId
     * @param userDO
     * @return
     * @throws GlobalException
     */
    @PostMapping("/detail")
    public Result detail(@RequestParam String orderId, @ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) throws GlobalException {
        GlWithdrawDetailResult result = withdrawHandler.withdrawDetail(userDO, orderId);
        return Result.genSuccessResult(result);
    }


    @PostMapping(value = "/last/status", produces = "application/json;charset=utf-8")
    public Result lastWithdrawStatus(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) throws GlobalException {
        WithdrawResult result = withdrawHandler.getLastWithdrawDetail(userDO);
        return Result.genSuccessResult(result);
    }

    /**
     * 提现结果后台通知
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
            log.error("withdrawController_notify_error = {}", e);
            return;
        }
    }

    /**
     * 风云聚合回调通知接口
     *
     * @param merchantId
     * @param request
     * @param response
     */
    @RequestMapping(value = "/notifyForFengYun/{merchantId}", method = {RequestMethod.POST, RequestMethod.GET})
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
            log.error("notifyForTransfer_error = {}", e);
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
}
