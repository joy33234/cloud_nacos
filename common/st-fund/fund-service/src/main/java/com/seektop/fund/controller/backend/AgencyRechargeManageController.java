package com.seektop.fund.controller.backend;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.recharge.AgencyRechargeBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.recharge.agency.AgencySwitchDto;
import com.seektop.fund.controller.backend.param.recharge.agency.AgencySwitchRequestDto;
import com.seektop.fund.controller.backend.param.recharge.agency.LevelSettingDto;
import com.seektop.fund.controller.forehead.param.recharge.AgencyRechargePaymentInfoDO;
import com.seektop.fund.controller.forehead.param.recharge.RechargeSubmitDO;
import com.seektop.fund.controller.forehead.param.recharge.RechargeTransferDO;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.handler.RechargeHandler;
import com.seektop.fund.handler.RechargeInsteadPaymentHandler;
import com.seektop.fund.model.AgencyRecharge;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.GlPaymentChannelBank;
import com.seektop.fund.payment.GlRechargeHandlerManager;
import com.seektop.fund.payment.GlRechargeResult;
import com.seektop.fund.payment.GlRechargeTransferResult;
import com.seektop.fund.payment.RechargeSubmitResponse;
import com.seektop.fund.vo.AgencyRechargeQueryDto;
import com.seektop.user.service.GlAdminService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/manage/fund/finance/agency/recharge")
@Slf4j
public class AgencyRechargeManageController extends FundBackendBaseController {
    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;
    @Reference(retries = 2, timeout = 3000)
    private GlAdminService glAdminService;
    @Resource
    private RedisService redisService;
    @Resource
    private AgencyRechargeBusiness agencyRechargeService;
    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankService;
    @Resource
    private GlRechargeHandlerManager glRechargeHandlerManager;
    @Resource(name = "rechargeHandler")
    private RechargeHandler rechargeHandler;
    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;
    @Autowired
    private RechargeInsteadPaymentHandler rechargeInsteadPaymentHandler;

    @PostMapping(value = "/list", produces = "application/json;charset=utf-8")
    public Result list(AgencyRechargeQueryDto queryDto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) {
        PageInfo<AgencyRecharge> datas = agencyRechargeService.query(queryDto);
        //遍历查询redis，拼接有效时长
        if (datas != null && datas.getList() != null) {
            datas.getList().stream().forEach(r -> {
                Long ttl = redisService.getTTL(RedisKeyHelper.AGENT_RECHARGE + r.getUserName() + "-" + r.getId());
                r.setTtl(ttl);
            });
        }
        return Result.genSuccessResult(datas);
    }

    @PostMapping(value = "/payment/info", produces = "application/json;charset=utf-8")
    public Result paymentInfo(@Validated AgencyRechargePaymentInfoDO rechargePaymentInfoDO) {
        return rechargeInsteadPaymentHandler.paymentInfo(rechargePaymentInfoDO);
    }


    @RequestMapping(value = "/do/submit", method = {RequestMethod.GET, RequestMethod.POST})
    public void doSubmit(@Validated RechargeSubmitDO rechargeSubmitDO,
                         HttpServletRequest request,
                         HttpServletResponse response) throws GlobalException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            GlAdminDO adminDO = getAdminByToken(rechargeSubmitDO.getToken());
            if (adminDO == null) {
                String message = glRechargeHandlerManager.rechargeSubmitFailedHtml("管理员不存在");
                out.print(LanguageLocalParser.key(FundLanguageMvcEnum.ADMIN_NOT_EXIST)
                        .withDefaultValue(message).parse(rechargeSubmitDO.getLanguage()));
                return;
            }
            RPCResponse<GlUserDO> rpcResponse = glUserService.getUserInfoByUsername(rechargeSubmitDO.getUserName());
            if (RPCResponseUtils.isFail(rpcResponse)) {
                String message = glRechargeHandlerManager.rechargeSubmitFailedHtml("用户名不存在");
                out.print(LanguageLocalParser.key(FundLanguageMvcEnum.USER_NOT_EXIST)
                        .withDefaultValue(message).parse(rechargeSubmitDO.getLanguage()));
            }
            GlUserDO userDO = rpcResponse.getData();

            AgencyRecharge agencyRecharge = agencyRechargeService.findById(rechargeSubmitDO.getAgencyId());
            if (agencyRecharge == null) {
                String message = glRechargeHandlerManager.rechargeSubmitFailedHtml("代客充值订单不存在");
                out.print(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_AGENT_ORDER_NOT_EXIST)
                        .withDefaultValue(message).parse(rechargeSubmitDO.getLanguage()));
                return;
            }

            if (agencyRecharge.getOrderId() != null) {
                String message = glRechargeHandlerManager.rechargeSubmitFailedHtml("代客充值订单已提交,请勿重复刷新");
                out.print(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_AGENT_ORDER_SUBMITED)
                        .withDefaultValue(message).parse(rechargeSubmitDO.getLanguage()));
                return;
            }
            // 生成充值结果
            rechargeSubmitDO.setHeaderDeviceId(FundConstant.AGENCY_DEVICE_UUID);
            rechargeSubmitDO.setPayType(FundConstant.AGENT_TYPE);
            GlRechargeResult rechargeResult = rechargeHandler.doRechargeSubmit(rechargeSubmitDO, userDO, request);

            // 充值记录入库&上报
            agencyRechargeService.doAgencyRechargeSubmit(rechargeResult.getTradeId(), userDO, rechargeSubmitDO.getAgencyId(), adminDO);
            log.info("============ doSubmit.rechargeResult:{}", JSON.toJSONString(rechargeResult));
            if (null != rechargeResult) {
                //充值失败
                if (rechargeResult.getErrorCode() == 1) {
                    String message = LanguageLocalParser.key(FundLanguageDicEnum.RECHARGE_CREATE_ORDER_ERROR)
                            .withParam("" + rechargeResult.getErrorCode())
                            .withDefaultValue( rechargeResult.getErrorMsg())
                            .parse(rechargeSubmitDO.getLanguage());
                    out.print(glRechargeHandlerManager.rechargeSubmitFailedHtml(message));
                    return;
                }
                if (rechargeResult.getErrorCode() == 0) {
                    RechargeSubmitResponse rechargeSubmitResponse = glRechargeHandlerManager.rechargeSubmitSuccess(rechargeResult);
                    response.setContentType(rechargeSubmitResponse.getContentType());
                    if (rechargeSubmitResponse.isRedirect()) {
                        response.sendRedirect(rechargeSubmitResponse.getContent());
                    } else {
                        out.print(rechargeSubmitResponse.getContent());
                    }
                    return;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (out != null) {
                String message = LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_CREATE_ORDER_ERROR)
                        .withDefaultValue( "订单创建失败")
                        .parse(rechargeSubmitDO.getLanguage());
                out.print(glRechargeHandlerManager.rechargeSubmitFailedHtml(message));
            }
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }

    }

    @RequestMapping(value = "/do/transfer", method = {RequestMethod.POST, RequestMethod.GET})
    public Result doTransfer(@Validated RechargeTransferDO rechargeTransferDO,
                             @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO,
                             HttpServletRequest request) throws GlobalException {
        rechargeTransferDO.setPayType(FundConstant.AGENT_TYPE);
        RPCResponse<GlUserDO> rpcResponse = glUserService.getUserInfoByUsername(rechargeTransferDO.getUserName());
        if (RPCResponseUtils.isFail(rpcResponse)) {
            Result result = Result.newBuilder().fail(ResultCode.INVALID_PARAM).build();
            result.setKeyConfig(FundLanguageMvcEnum.INVALID_PARAM);
            return result;
        }
        GlRechargeTransferResult result = rechargeHandler.doRechargeTransfer(rechargeTransferDO, rpcResponse.getData(), request);

        agencyRechargeService.doAgencyRechargeSubmit(result.getTradeNo(), rpcResponse.getData(), rechargeTransferDO.getAgencyId(), adminDO);
        return Result.genSuccessResult(result);
    }

    /**
     * 获取银行卡列表
     *
     * @return
     */
    @RequestMapping(value = "/getBanks", method = {RequestMethod.POST, RequestMethod.GET})
    public Result getBanks(Integer channelId, ParamBaseDO paramBaseDO) {
        List<GlPaymentChannelBank> banks = glPaymentChannelBankService.getChannelBank(channelId);
        if (banks == null || banks.size() == 0) {
            return Result.genSuccessResult(banks);
        }

        List<Map<String, String>> list = banks.stream().reduce(new ArrayList<>(), (list2, bank) -> {
            list2.add(new HashMap<String, String>() {{
                put("bankId", String.valueOf(bank.getBankId()));
                put("bankName", bank.getBankName());
            }});
            return list2;
        }, (bank1, bank2) -> bank2);
        return Result.genSuccessResult(list);
    }

    /**
     * 代客充值 层级设置
     *
     * @param dto
     * @return
     */
    @PostMapping(value = "/setLevelSwitch", produces = "application/json;charset=utf-8")
    public Result setLevelSwitch(@RequestBody AgencySwitchRequestDto dto) {
        //全局开关
        if (dto.getValue() == 1) {
            redisService.set(RedisKeyHelper.AGENT_RECHARGE_SWITCH_CONFIG, 1, -1);
        } else {
            redisService.delete(RedisKeyHelper.AGENT_RECHARGE_SWITCH_CONFIG);
        }

        List<GlFundUserlevel> levels = glFundUserlevelBusiness.findAll();
        for (GlFundUserlevel le : levels) {
            if (dto.getOpen().contains(le.getLevelId())) {
                userLevelSwitch(le.getLevelId(), 1);
            } else {
                userLevelSwitch(le.getLevelId(), 0);
            }
        }
        return Result.genSuccessResult(dto);
    }


    @PostMapping(value = "/getLevelSwitch", produces = "application/json;charset=utf-8")
    public Result getLevelSwitch(ParamBaseDO paramBaseDO) {
        AgencySwitchDto dto = new AgencySwitchDto();
        Integer value = redisService.get(RedisKeyHelper.AGENT_RECHARGE_SWITCH_CONFIG, Integer.class);
        if (value == null) {
            value = 0;
        }
        dto.setValue(value);

        List<LevelSettingDto> levelSettingDtoList = Lists.newArrayList();
        List<GlFundUserlevel> levels = glFundUserlevelBusiness.findAll();
        boolean isAllOpen = true;
        boolean isAllClose = true;
        if (!CollectionUtils.isEmpty(levels)) {
            for (GlFundUserlevel le : levels) {
                if (redisService.exists(RedisKeyHelper.AGENCY_USER_LEVEL + le.getLevelId())) {
                    levelSettingDtoList.add(new LevelSettingDto(le.getLevelId(), 1, le.getName()));
                    isAllClose = false;
                } else {
                    levelSettingDtoList.add(new LevelSettingDto(le.getLevelId(), 0, le.getName()));
                    isAllOpen = false;
                }
            }
        }
        if (isAllOpen) {
            levelSettingDtoList.add(new LevelSettingDto(-1, 1, "全部"));
        }
        if (isAllClose) {
            levelSettingDtoList.add(new LevelSettingDto(-1, 0, "全部"));
        }
        dto.setLevelSettingDtoList(levelSettingDtoList);
        return Result.genSuccessResult(dto);
    }

    /**
     * 接口默认全部关闭
     *
     * @param levelId
     * @param status
     * @return
     */
    public void userLevelSwitch(Integer levelId, Integer status) {
        //打开层级
        if (status == 1) {
            redisService.set(RedisKeyHelper.AGENCY_USER_LEVEL + levelId, 1, -1);
        }
        //关闭层级
        if (status == 0) {
            redisService.delete(RedisKeyHelper.AGENCY_USER_LEVEL + levelId);
        }
    }
}
