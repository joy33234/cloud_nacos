package com.seektop.fund.controller.backend;

import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlWithdrawEffectBetBusiness;
import com.seektop.fund.controller.backend.dto.withdraw.*;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.handler.UserWithdrawEffectHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户流水详情相关接口
 */
@Slf4j
@RestController
@RequestMapping("/manage/fund/effect")
public class UserWithdrawEffectController extends FundBackendBaseController {

    @Resource
    private GlWithdrawEffectBetBusiness glWithdrawEffectBetBusiness;
    @Autowired
    private UserWithdrawEffectHandler userWithdrawEffectHandler;

    /**
     * 查询用户提现流水已完成信息
     *
     * @param userId
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/user", produces = "application/json;charset=utf-8")
    public Result userEffectInfo(@RequestParam Integer userId,@RequestParam(required = false) String coinCode) throws GlobalException {
        List<GlWithdrawAmountStatusResult> result = glWithdrawEffectBetBusiness.queryWithdrawEffectInfo(userId,coinCode);
        return Result.genSuccessResult(result);
    }

    /**
     * 后台用户资金流水调整申请
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/adjust", produces = "application/json;charset=utf-8")
    public Result adjustRequest(@Validated EffectAdjustDto effectAdjustDto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        userWithdrawEffectHandler.adjust(effectAdjustDto, admin);
        return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.USER_EFFECT_SUBMIT).parse(effectAdjustDto.getLanguage()));
    }

    /**
     * 后台用户资金流水清零申请
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/remove", produces = "application/json;charset=utf-8")
    public Result removeRequest(@Validated EffectRemoveDto cleanDto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        userWithdrawEffectHandler.remove(cleanDto, admin);
        return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.USER_EFFECT_SUBMIT).parse(cleanDto.getLanguage()));
    }

    @PostMapping(value = "/clean", produces = "application/json;charset=utf-8")
    public Result cleanRequest(@Validated EffectCleanDto cleanDto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        userWithdrawEffectHandler.clean(cleanDto, admin);
        return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.USER_EFFECT_SUBMIT).parse(cleanDto.getLanguage()));
    }

    /**
     * 后台用户资金流水恢复
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/recover", produces = "application/json;charset=utf-8")
    public Result recoverRequest(@Validated EffectRecoverDto recoverDto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) throws GlobalException {
        userWithdrawEffectHandler.recover(recoverDto, admin);
        return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.USER_EFFECT_SUBMIT).parse(recoverDto.getLanguage()));

    }
}
