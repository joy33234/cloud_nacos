package com.seektop.fund.controller.forehead;

import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.fund.controller.forehead.param.proxy.ProxyPayoutDO;
import com.seektop.fund.controller.forehead.result.ProxyFundsResult;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.enums.ProxyPayoutEnum;
import com.seektop.fund.handler.FundProxyHandler;
import com.seektop.user.dto.result.GlSecurityPasswordDO;
import com.seektop.user.service.GlProxyRelatedUserService;
import com.seektop.user.service.GlUserSecurityService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping(value = "/forehead/fund/funds", produces = "application/json;charset=UTF-8")
public class FundsProxyController extends GlFundForeheadBaseController {

    @Resource
    private FundProxyHandler fundProxyHandler;

    @Resource
    private RedisService redisService;

    @Reference(timeout = 3000, retries = 2)
    private GlUserService glUserService;

    @Reference(timeout = 3000, retries = 2)
    private GlUserSecurityService glUserSecurityService;

    @Reference(timeout = 3000, retries = 2)
    private GlProxyRelatedUserService glProxyRelatedUserService;

    @PostMapping("/wallet/info")
    public Result walletInfo(@ModelAttribute(name = "userInfo", binding = false) GlUserDO userDO, ParamBaseDO paramBaseDO) {
        if (userDO == null) return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.LOGIN_INVALID)
                .withDefaultValue("登录失效").parse(paramBaseDO.getLanguage()));
        if (userDO.getUserType() != UserConstant.UserType.PROXY) return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.PROXY_INFO_NOT_ACQUIRED)
                .withDefaultValue("无法获取代理信息，请稍后重试").parse(paramBaseDO.getLanguage()));
        return Result.genSuccessResult(fundProxyHandler.walletInfo(userDO.getId()));
    }

    @PostMapping("/payout")
    public Result payout(
            @RequestHeader String token,
            @Validated ProxyPayoutDO proxyPayoutDO,
            @ModelAttribute(name = "userInfo", binding = false) GlUserDO userDO, ParamBaseDO paramBaseDO) {
        if (userDO == null) return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.LOGIN_INVALID)
                .withDefaultValue("登录失效").parse(paramBaseDO.getLanguage()));
        if (userDO.getUserType() != UserConstant.UserType.PROXY) return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.PROXY_INFO_NOT_ACQUIRED)
                .withDefaultValue("无法获取代理信息，请稍后重试").parse(paramBaseDO.getLanguage()));
        //接口请求限制
        if (redisService.incrBy(RedisKeyHelper.LAST_PROXY_CREDIT_OPT + userDO.getId(), 1) > 1) {
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.FREQUENT_OPERATIONS)
                    .withDefaultValue("操作过于频繁，建议您稍后再试").parse(paramBaseDO.getLanguage()));
        }
        redisService.setTTL(RedisKeyHelper.LAST_PROXY_CREDIT_OPT + userDO.getId(), 2);

        if (userDO.getStatus() == UserConstant.Status.HALF_LOCKED || userDO.getStatus() == UserConstant.Status.LOCKED) {
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.OPERATE_ERROR_CONTACT_CUSTOMER_SERVICE)
                    .withDefaultValue("暂不能进行此操作，请联系客服确认账号状态").parse(paramBaseDO.getLanguage()));
        }

        //校验金额
        if (proxyPayoutDO.getAmount() <= 0) {
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.AMOUNT_LIMIT_ZERO)
                    .withDefaultValue("操作金额不能小于等于0").parse(paramBaseDO.getLanguage()));
        }

        //校验参数
        List<Integer> valueList = ProxyPayoutEnum.valueList;
        if (!valueList.contains(proxyPayoutDO.getType())) {
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.PARAM_TYPE_ERROR)
                    .withDefaultValue("操作类型参数错误").parse(paramBaseDO.getLanguage()));
        }

        //校验目标用户
        List<GlUserDO> targets = new ArrayList<>();
        if (proxyPayoutDO.getType() == ProxyPayoutEnum.TRANSFER_MEMBER.getCode()) {
            GlUserDO target = glProxyRelatedUserService.getRelatedUser(userDO.getId()).getData();
            if (null == target) {
                return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.OPERATE_ERROR_CONTACT_CUSTOMER_SERVICE)
                        .withDefaultValue("暂不能进行此操作，请联系客服确认账号状态").parse(paramBaseDO.getLanguage()));
            }
            targets.add(target);
        } else {
            String targetId = Optional.ofNullable(proxyPayoutDO.getTargetId()).orElse("");
            for (String uid:targetId.split(",")){
                if (!org.apache.commons.lang3.StringUtils.isNumeric(uid)) return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.TARGET_USER_NOT_EXIST)
                        .withDefaultValue("目标用户不存在").parse(paramBaseDO.getLanguage()));
                GlUserDO target = glUserService.findById(Integer.valueOf(uid)).getData();
                if (ObjectUtils.isEmpty(target)
                        || (proxyPayoutDO.getType() == ProxyPayoutEnum.RECHARGE.getCode() && target.getUserType() != UserConstant.UserType.PLAYER)
                        || (proxyPayoutDO.getType() == ProxyPayoutEnum.TRANSFER.getCode() && target.getUserType() != UserConstant.UserType.PROXY)) {
                    return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.TARGET_USER_NOT_EXIST)
                            .withDefaultValue("目标用户不存在").parse(paramBaseDO.getLanguage()));
                }
                if (null == target.getParentId() || target.getParentId().intValue() != userDO.getId().intValue()) {
                    return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.TARGET_USER_NOT_DIRECT_SUB)
                            .withDefaultValue("目标用户不是直属下级").parse(paramBaseDO.getLanguage()));
                }
                targets.add(target);
            }
        }

        //token级别24小时内免重复输入密码
        GlSecurityPasswordDO pass = glUserSecurityService.getSecurityPassword(userDO.getId()).getData();
        if (pass == null) {
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.FUND_PASSWORD_NOT_SET)
                    .withDefaultValue("您尚未设置资金密码").parse(paramBaseDO.getLanguage()));
        }
        if (StringUtils.isEmpty(proxyPayoutDO.getPassword())) {
            if (!redisService.exists(RedisKeyHelper.PROXY_PAYOUT_PASSWORD + token)) {
                return Result.genFailResult(ResultCode.PAYOUT_PWD_WRONG.getCode(), LanguageLocalParser.key(FundLanguageMvcEnum.FUND_PASSWORD_ERROR)
                        .withDefaultValue("资金密码错误").parse(paramBaseDO.getLanguage()));
            }
        } else {
            if (!pass.getPassword().equals(DigestUtils.md5Hex(proxyPayoutDO.getPassword()))) {
                return Result.genFailResult(ResultCode.PAYOUT_PWD_WRONG.getCode(), LanguageLocalParser.key(FundLanguageMvcEnum.FUND_PASSWORD_ERROR)
                        .withDefaultValue("资金密码错误").parse(paramBaseDO.getLanguage()));
            }
        }
        redisService.set(RedisKeyHelper.PROXY_PAYOUT_PASSWORD + token, "1", 24 * 3600);
        Result payout = Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.SERVICE_IS_BUSY)
                .withDefaultValue("服务忙,请稍后再试!").parse(paramBaseDO.getLanguage()));
        ProxyFundsResult result = new ProxyFundsResult();
        if (targets.size()>0){
            for (GlUserDO target :targets){
                payout = fundProxyHandler.payout(proxyPayoutDO, target, userDO);
                ProxyFundsResult data = (ProxyFundsResult) payout.getData();
                if (data==null) {
                    result.getFailUser().add(target.getUsername());
                } else {
                    result.getSucUser().add(target.getUsername());
                    result.setAmount(result.getAmount()==null?data.getAmount():result.getAmount().add(data.getAmount()));
                    result.setBalanceAfter(data.getBalanceAfter());
                    result.setCreditAfter(data.getCreditAfter());
                    result.setCreditAmountAfter(data.getCreditAmountAfter());
                    result.setBalanceBefore(result.getBalanceBefore()==null?data.getBalanceBefore():result.getBalanceBefore());
                    result.setCreditBefore(result.getCreditBefore()==null?data.getCreditBefore():result.getCreditBefore());
                }
            }
        }
        if (result.getSucUser().size()>0) payout = Result.genSuccessResult(result);
        return payout;
    }

    @PostMapping("/subordinateProxyList")
    public Result subordinateProxyList(@ModelAttribute(name = "userInfo", binding = false) GlUserDO userDO,
                                       @RequestParam String username, ParamBaseDO paramBaseDO) {
        if (userDO == null) return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.LOGIN_INVALID)
                .withDefaultValue("登录失效").parse(paramBaseDO.getLanguage()));
        if (userDO.getUserType() != UserConstant.UserType.PROXY) return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.PROXY_INFO_NOT_ACQUIRED)
                .withDefaultValue("无法获取代理信息，请稍后重试").parse(paramBaseDO.getLanguage()));
        return Result.genSuccessResult(fundProxyHandler.findSubordinateProxyList(userDO.getId(), username));
    }
}

