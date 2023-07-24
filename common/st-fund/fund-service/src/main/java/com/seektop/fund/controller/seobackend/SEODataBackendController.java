package com.seektop.fund.controller.seobackend;

import com.seektop.common.encrypt.annotation.Encrypt;
import com.seektop.common.encrypt.annotation.EncryptField;
import com.seektop.common.encrypt.enums.EncryptTypeEnum;
import com.seektop.common.encrypt.enums.builder.Encryptor;
import com.seektop.common.rest.Result;
import com.seektop.common.validator.group.CommonValidate;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.proxy.ProxyAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.*;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawAmountStatusResult;
import com.seektop.fund.controller.backend.result.FundUserLevelListResult;
import com.seektop.fund.controller.backend.result.recharge.GlRechargeCollectResult;
import com.seektop.fund.controller.backend.result.withdraw.GlWithdrawCollectResult;
import com.seektop.fund.controller.forehead.param.proxy.ProxyPayoutDO;
import com.seektop.fund.controller.forehead.result.ProxyFundsResult;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.enums.ProxyPayoutEnum;
import com.seektop.fund.handler.FundProxyHandler;
import com.seektop.fund.handler.RechargeRecordHandler;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.vo.GlWithdrawAllCollect;
import com.seektop.fund.vo.GlWithdrawQueryDto;
import com.seektop.fund.vo.RechargeQueryDto;
import com.seektop.fund.vo.WithdrawVO;
import com.seektop.user.dto.result.GlSecurityPasswordDO;
import com.seektop.user.service.GlProxyRelatedUserService;
import com.seektop.user.service.GlUserSecurityService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping(value = "/proxy/seo/manage/fund")
public class SEODataBackendController extends DataBackendBaseController {

    @Resource
    private RechargeRecordHandler rechargeRecordHandler;
    @Resource
    private GlWithdrawRecordBusiness glWithdrawRecordBusiness;
    @Resource
    private GlWithdrawEffectBetBusiness glWithdrawEffectBetBusiness;
    @Autowired
    private GlFundUserlevelBusiness fundUserlevelBusiness;
    @Resource
    private GlPaymentBusiness glPaymentBusiness;
    @Resource
    private FundProxyHandler fundProxyHandler;
    @Resource
    private GlWithdrawUserBankCardBusiness bankCardBusiness;
    @Resource
    private GlWithdrawUserBankCardBusiness glWithdrawUserBankCardService;
    @Resource
    private GlWithdrawUserUsdtAddressBusiness glWithdrawUserUsdtAddressBusiness;
    @DubboReference
    private GlUserService glUserService;
    @DubboReference(timeout = 3000, retries = 2)
    private GlUserSecurityService glUserSecurityService;
    @DubboReference(timeout = 3000, retries = 2)
    private GlProxyRelatedUserService glProxyRelatedUserService;
    /**
     * 充值记录列表
     */
    @PostMapping(value = "/recharge/record/list", produces = "application/json;charset=utf-8")
    public Result rechargeList(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO, RechargeQueryDto queryDto) throws GlobalException {
        queryDto.setIncludeTotal(true);
        if (StringUtils.isBlank(queryDto.getUserName())&& StringUtils.isBlank(queryDto.getOrderId())) return Result.genFailResult("参数错误!");
        if (StringUtils.isNotBlank(queryDto.getUserName())&&(!userCheck(adminDO,queryDto.getUserName()))) return Result.genFailResult("无权参看该用户!");
        GlRechargeCollectResult<GlRechargeDO> resultPageInfo = rechargeRecordHandler.findRechargeRecordPageList(queryDto);
        if (StringUtils.isNotBlank(queryDto.getOrderId())){
            List<GlRechargeDO> list = resultPageInfo.getList();
            if (list.size()>0&&(!userCheck(adminDO,list.get(0).getUserId()))) return Result.genFailResult("无权参看该用户!");
        }
        return Result.genSuccessResult(resultPageInfo);
    }
    /**
     * 代理详情-充值记录-导出
     * @param queryDto
     * @return
     */
    @PostMapping("/recharge/record/list/export")
    public Result rechargeListExport(@Validated(value = CommonValidate.class) RechargeQueryDto queryDto,
                                     @ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO) {
        queryDto.setUserIdList(Collections.singletonList(queryDto.getUserId()));
        if (StringUtils.isNotBlank(queryDto.getUserName())&&(!userCheck(adminDO,queryDto.getUserName()))) return Result.genFailResult("无权参看该用户!");
        if (queryDto.getUserId()!=null&&(!userCheck(adminDO,queryDto.getUserId()))) return Result.genFailResult("无权参看该用户!");
        queryDto.setDateType(1); //设置为充值时间查询
        rechargeRecordHandler.rechargeListExport(queryDto, adminDO.getId().intValue());
        return Result.genSuccessResult("正在导出，请稍后下载");
    }
    /**
     * 提现订单记录列表
     */
    @PostMapping(value = "/withdraw/record/list/history", produces = "application/json;charset=utf-8")
    public Result withdrawList(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO, GlWithdrawQueryDto queryDto) throws GlobalException {
        if (StringUtils.isBlank(queryDto.getUserName())&& StringUtils.isBlank(queryDto.getOrderId())) return Result.genFailResult("参数错误!");
        if (StringUtils.isNotBlank(queryDto.getUserName())&&(!userCheck(adminDO,queryDto.getUserName()))) return Result.genFailResult("无权参看该用户!");
        GlWithdrawCollectResult<WithdrawVO> pageInfo = glWithdrawRecordBusiness.getWithdrawHistoryPageList(queryDto);
        if (StringUtils.isNotBlank(queryDto.getOrderId())){
            List<WithdrawVO> list = pageInfo.getList();
            if (list.size()>0&&(!userCheck(adminDO,list.get(0).getUserId()))) return Result.genFailResult("无权参看该用户!");
        }
        return Result.genSuccessResult(pageInfo);
    }
    /**
     * 代理详情-提现订单记录
     * @param queryDto
     * @return
     */
    @PostMapping("/withdraw/record/list/export")
    public Result withdrawListExport(@Validated(value = com.seektop.common.validatorgroup.CommonValidate.class) GlWithdrawQueryDto queryDto,
                                     @ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO) {
        queryDto.setDateType(1); //设置体现时间查询
//        queryDto.setUserIdList(Collections.singletonList(queryDto.getUserId()));
        if (StringUtils.isNotBlank(queryDto.getUserName())&&(!userCheck(adminDO,queryDto.getUserName()))) return Result.genFailResult("无权参看该用户!");
        if (queryDto.getUserId()!=null&&(!userCheck(adminDO,queryDto.getUserId()))) return Result.genFailResult("无权参看该用户!");
        glWithdrawRecordBusiness.withdrawListExport(queryDto, adminDO.getId().intValue());
        return Result.genSuccessResult("正在导出，请稍后下载");
    }
    /**
     * 会员详情-充值记录分页查询
     */
    @PostMapping(value = "/recharge/record/member/list", produces = "application/json;charset=utf-8")
    public Result userList(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO, RechargeQueryDto queryDto) throws GlobalException {
        if (queryDto.getUserId() == null) {
            return Result.genFailResult("用户id不能为空");
        }
        if (!userCheck(adminDO,queryDto.getUserId())) return Result.genFailResult("无权参看该用户!");
        queryDto.setNeedName(1);
        queryDto.setIncludeTotal(true);
        GlRechargeCollectResult<GlRechargeDO> resultPageInfo = rechargeRecordHandler.findRechargeRecordPageList(queryDto);
        return Result.genSuccessResult(resultPageInfo);
    }
    /**
     * 查询用户提现流水已完成信息
     *
     * @param userId
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/effect/user", produces = "application/json;charset=utf-8")
    public Result userEffectInfo(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO, @RequestParam Integer userId, @RequestParam(required = false) String coinCode) throws GlobalException {
        if (!userCheck(adminDO,userId)) return Result.genFailResult("无权参看该用户!");
        List<GlWithdrawAmountStatusResult> result = glWithdrawEffectBetBusiness.queryWithdrawEffectInfo(userId,coinCode);
        return Result.genSuccessResult(result);
    }
    /**
     * 查询所有用户层级
     * @param levelType 层级类型 : -1 所有,0 会员,1 代理
     * @return
     */
    @RequestMapping("/user/level/list")
    public Result list(@RequestParam(required = false, defaultValue = "-1", name = "levelType") Integer levelType) {
        List<FundUserLevelListResult> list = fundUserlevelBusiness.findByLevelType(levelType);
        return Result.genSuccessResult(list);
    }

    /**
     * 所有支付方式
     */
    @PostMapping(value = "/payment/list", produces = "application/json;charset=utf-8")
    public Result paymentList() {
        return Result.genSuccessResult(glPaymentBusiness.findAll());
    }
    /**
     * 代理代充额度
     */
    @PostMapping("/funds/wallet/info")
    public Result walletInfo(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO, String proxyName) {
        if (adminDO == null) return Result.genFailResult("登录失效");
        GlUserDO data = glUserService.getUserInfoByUsername(proxyName).getData();
        if (data==null||data.getUserType() != UserConstant.UserType.PROXY) return Result.genFailResult("无法获取代理信息，请稍后重试");
        if (!adminDO.getProxyIds().contains(data.getId())) return Result.genFailResult("无权获取信息");
        return Result.genSuccessResult(fundProxyHandler.walletInfo(data.getId()));
    }
    @PostMapping("/funds/payout")
    public Result payout(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO,
                         @Validated ProxyPayoutDO proxyPayoutDO, String proxyName) {
        if (adminDO == null) return Result.genFailResult("登录失效");
        GlUserDO proxy = glUserService.getUserInfoByUsername(proxyName).getData();
        if (proxy==null||proxy.getUserType() != UserConstant.UserType.PROXY) return Result.genFailResult("无法获取代理信息，请稍后重试");
        if (!adminDO.getProxyIds().contains(proxy.getId())) return Result.genFailResult("无权获取信息");
        //接口请求限制
        if (redisService.incrBy(RedisKeyHelper.LAST_PROXY_CREDIT_OPT + proxy.getId(), 1) > 1) {
            return Result.genFailResult("操作过于频繁，建议您稍后再试");
        }
        redisService.setTTL(RedisKeyHelper.LAST_PROXY_CREDIT_OPT + proxy.getId(), 2);

        if (proxy.getStatus() == UserConstant.Status.HALF_LOCKED || proxy.getStatus() == UserConstant.Status.LOCKED) {
            return Result.genFailResult("暂不能进行此操作，请联系客服确认账号状态");
        }

        //校验金额
        if (proxyPayoutDO.getAmount() <= 0) {
            return Result.genFailResult("操作金额不能小于等于0");
        }

        //校验参数
        List<Integer> valueList = ProxyPayoutEnum.valueList;
        if (!valueList.contains(proxyPayoutDO.getType())) {
            return Result.genFailResult("操作类型参数错误");
        }
        proxyPayoutDO.setType(ProxyPayoutEnum.RECHARGE.getCode());
        //校验目标用户
        List<GlUserDO> targets = new ArrayList<>();
        String targetId = Optional.ofNullable(proxyPayoutDO.getTargetId()).orElse("");
        for (String uid:targetId.split(",")){
            if (!org.apache.commons.lang3.StringUtils.isNumeric(uid)) return Result.genFailResult("目标用户不存在");
            GlUserDO target = glUserService.findById(Integer.valueOf(uid)).getData();
            if (ObjectUtils.isEmpty(target)
                    || (proxyPayoutDO.getType() == ProxyPayoutEnum.RECHARGE.getCode() && target.getUserType() != UserConstant.UserType.PLAYER)
                    || (proxyPayoutDO.getType() == ProxyPayoutEnum.TRANSFER.getCode() && target.getUserType() != UserConstant.UserType.PROXY)) {
                return Result.genFailResult("目标用户不存在");
            }
            if (null == target.getParentId() || target.getParentId().intValue() != proxy.getId().intValue()) {
                return Result.genFailResult("目标用户不是直属下级");
            }
            targets.add(target);
        }
        //token级别24小时内免重复输入密码
        GlSecurityPasswordDO pass = glUserSecurityService.getSecurityPassword(proxy.getId()).getData();
        if (pass == null) {
            return Result.genFailResult("您尚未设置资金密码");
        }
        if (!pass.getPassword().equals(DigestUtils.md5Hex(proxyPayoutDO.getPassword()))) {
            return Result.genFailResult(ResultCode.PAYOUT_PWD_WRONG.getCode(), "资金密码错误");
        }
        proxyPayoutDO.setRemarks(Optional.ofNullable(proxyPayoutDO.getRemarks()).orElse("")+"管理员:"+adminDO.getUsername());
        Result payout = Result.genFailResult("服务忙,请稍后再试!");
        ProxyFundsResult result = new ProxyFundsResult();
        if (targets.size()>0){
            for (GlUserDO target :targets){
                payout = fundProxyHandler.payout(proxyPayoutDO, target, proxy);
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

    /**
     * 会员详情-提现记录
     *
     * @param queryDto
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/withdraw/record/member/withdraw", produces = "application/json;charset=utf-8")
    public Result withdraw(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO, GlWithdrawQueryDto queryDto) throws GlobalException {
        if (queryDto.getUserName() == null) {
            return Result.genFailResult("用户名不能为空");
        }
        if (!userCheck(adminDO,queryDto.getUserName())) return Result.genFailResult("无权参看该用户!");
        queryDto.setNeedName(1);
        GlWithdrawCollectResult<WithdrawVO> pageInfo = glWithdrawRecordBusiness.getWithdrawHistoryPageList(queryDto);
        GlWithdrawAllCollect collect = glWithdrawRecordBusiness.getMemberWithdrawTotal(queryDto);
        pageInfo.setGlWithdrawAllCollect(collect);
        return Result.genSuccessResult(pageInfo);
    }

    /**
     * 查询会员银行卡信息
     * @param userId
     * @return
     */
    @Encrypt(values = {@EncryptField(fieldName = "name", typeEnums = EncryptTypeEnum.NAME),
            @EncryptField(fieldName = "cardNo", typeEnums = EncryptTypeEnum.BANKCARD),
            @EncryptField(fieldName = "address", typeEnums = EncryptTypeEnum.ADDRESS)})
    @PostMapping("/userbankcard/detail/bank")
    public Result bank(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO, @RequestParam Integer userId) throws GlobalException {
        if (!userCheck(adminDO,userId)) return Result.genFailResult("无权参看该用户!");
        List<GlWithdrawUserBankCard> cardList = bankCardBusiness.getUserCardInfo(userId);
        return Result.genSuccessResult(cardList);
    }
    /**
     * 查询用户已绑定USDT地址
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/usdt/list")
    public Result usdtList(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO, @NotNull Integer userId) throws GlobalException {
        if (!userCheck(adminDO,userId)) return Result.genFailResult("无权参看该用户!");
        return Result.genSuccessResult(glWithdrawUserUsdtAddressBusiness.findByUserId(userId, null));
    }
    @PostMapping(value = "/userbankcard/list/user", produces = "application/json;charset=utf-8")
    public Result listUser(@ModelAttribute(name = "proxyAdmin", binding = false) ProxyAdminDO adminDO,
                           @RequestParam Integer userId, @RequestParam(required = false) Integer isNotHide, @RequestParam(required = false)Integer status) {
        if (!userCheck(adminDO,userId)) return Result.genFailResult("无权参看该用户!");
        return Result.genSuccessResult(this.bankCardList(userId,isNotHide,status));
    }
    private List<GlWithdrawUserBankCard> bankCardList(Integer userId, Integer isNotHide, Integer status){
        List<GlWithdrawUserBankCard> list = glWithdrawUserBankCardService.findUserCardList(userId);
        if (list != null && isNotHide == null) {
            for (GlWithdrawUserBankCard card : list) {
                card.setName(Encryptor.builderName().doEncrypt(card.getName()));
                card.setCardNo(Encryptor.builderBankCard().doEncrypt(card.getCardNo()));
            }
        }
        return !ObjectUtils.isEmpty(status) && status == 0 ?
                list.stream().filter(b->b.getStatus() == 0).collect(Collectors.toList())
                : list;
    }
}
