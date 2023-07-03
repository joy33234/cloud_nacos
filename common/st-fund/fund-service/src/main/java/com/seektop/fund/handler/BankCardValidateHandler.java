package com.seektop.fund.handler;

import com.google.common.collect.Lists;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBankBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.business.withdraw.WithdrawBankCardVerifyBusiness;
import com.seektop.fund.business.withdraw.WithdrawUserBankCardApplyBusiness;
import com.seektop.fund.controller.backend.dto.BankCardBankDto;
import com.seektop.fund.controller.backend.param.bankcard.BindCardForm;
import com.seektop.fund.controller.forehead.param.userCard.ApplyBindCardDto;
import com.seektop.fund.controller.forehead.param.userCard.ApplyBindCardV2Form;
import com.seektop.fund.controller.forehead.param.userCard.BindCardDto;
import com.seektop.fund.controller.forehead.param.userCard.BindCardV2Form;
import com.seektop.fund.handler.validation.*;
import com.seektop.fund.model.GlWithdrawBank;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.system.dto.BankCardDto;
import com.seektop.system.service.BankCardApiService;
import com.seektop.system.service.GlSystemApiService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class BankCardValidateHandler {

    @Reference(retries = 2, timeout = 3000)
    private GlUserService userService;
    @Autowired
    private RedisService redisService;
    @Reference(retries = 2, timeout = 3000)
    private GlSystemApiService systemApiService;
    @Reference(retries = 2, timeout = 3000)
    private BankCardApiService bankCardApiService;
    @Autowired
    private GlWithdrawUserBankCardBusiness cardBusiness;
    @Autowired
    private WithdrawUserBankCardApplyBusiness cardApplyBusiness;
    @Autowired
    private GlWithdrawBankBusiness bankBusiness;
    @Autowired
    private WithdrawBankCardVerifyBusiness verifyBusiness;

    /**
     * 数据校验
     *
     * @param bindCardDto
     * @param user
     * @throws GlobalException
     */
    public void validation(final BindCardDto bindCardDto, final GlUserDO user) throws GlobalException {
        Validator validator = new Validator();
        String name = bindCardDto.getName();
        String cardNo = bindCardDto.getCardNo();
        String code = bindCardDto.getCode();
        validator.add(new BankCardValidationArgs(name, cardNo));
        validator.add(new BankCardValidationUserInfo(user, name));
        validator.add(new ValidationLimit(String.format(RedisKeyHelper.BANK_BIND_LIMIT, user.getId()), redisService));
        validator.add(new BankCardValidationTimeLimit(name, cardNo, user.getId(), redisService)); // 校验次数限制
        if (UserConstant.Type.PROXY == user.getUserType()) { // 代理校验验证码
            validator.add(new ValidationCode(user, code, ProjectConstant.MSG_TYPE_CARDBIND, systemApiService));
        }
        validator.valid();
    }
    /**
     * 数据校验
     *
     * @param form
     * @param user
     * @throws GlobalException
     */
    public void validationV2(final BindCardV2Form form, final GlUserDO user) throws GlobalException {
        Validator validator = new Validator();
        String name = form.getName();
        String cardNo = form.getCardNo();
        String code = form.getCode();
        validator.add(new BankCardValidationArgs(name, cardNo));
        validator.add(new BankCardValidationUserInfo(user, name));
        validator.add(new ValidationLimit(String.format(RedisKeyHelper.BANK_BIND_LIMIT, user.getId()), redisService));
        validator.add(new BankCardValidationTimeLimit(name, cardNo, user.getId(), redisService)); // 校验次数限制
        validator.add(new ValidationCode(user, code, ProjectConstant.MSG_TYPE_CARDBIND, systemApiService)); // 校验验证码
        validator.valid();
    }

    /**
     * 数据校验
     * @param form
     * @param user
     * @param admin
     * @throws GlobalException
     */
    public void validation(final BindCardForm form, final GlUserDO user, final GlAdminDO admin) throws GlobalException {
        Validator validator = new Validator();
        String name = form.getName();
        String cardNo = form.getCardNo();
        validator.add(new BankCardValidationArgs(name, cardNo));
        validator.add(new BankCardValidationUserInfo(user, name));
        validator.add(new ValidationLimit(String.format(RedisKeyHelper.BANK_BIND_LIMIT, admin.getUserId()), redisService));
        validator.valid();

        GlWithdrawBank bank = bankBusiness.findById(form.getBankId());
        validator.add(ObjectUtils.isEmpty(bank), ResultCode.UNSUPPORTED_BANK_ERROR).valid();
        form.setBankName(bank.getBankName());
    }

    /**
     * 银行卡校验
     *
     * @param user
     * @param cardNo
     * @param cardList
     * @throws GlobalException
     */
    public void validation(final GlUserDO user, final String cardNo, final List<GlWithdrawUserBankCard> cardList) throws GlobalException {
        Validator validator = new Validator();
        // 已绑定的银行卡加上审核中的银行卡
        List<GlWithdrawUserBankCard> userCards = Lists.newArrayList(cardList);
        List<GlWithdrawUserBankCard> applyCards = cardApplyBusiness.findCardByUserId(user.getId());
        userCards.addAll(applyCards);
        validator.add(new BankCardValidationCard(user, userCards));
        // 检查银行卡是否已被绑定或已被申请人工绑卡
        validator.add(new BankCardValidationCardExist(cardNo, cardBusiness, cardApplyBusiness));
        validator.valid();
    }

    /**
     * 更新银行卡校验
     *
     * @param type 0 删除 1 更新
     * @param user
     * @param card
     */
    public void validation(final Integer type, final GlUserDO user, final GlWithdrawUserBankCard card,
                           final String code) throws GlobalException {
        Validator validator = new Validator();
        validator.add(card == null || !card.getUserId().equals(user.getId()), "银行卡不存在");
        if (0 == type) {
            validator.valid();
            RPCResponse<GlUserDO> response = userService.findById(user.getId());
            GlUserDO userDO = RPCResponseUtils.getData(response);
            validator.add(new ValidationCode(userDO, code, ProjectConstant.MSG_TYPE_CARDDEL, systemApiService));
        }
        validator.valid();
    }

    /**
     * 校验人工绑卡申请
     *
     * @param user
     * @param bindCardDto
     * @throws GlobalException
     */
    public void validation(final GlUserDO user, final ApplyBindCardDto bindCardDto, final List<GlWithdrawUserBankCard> cardList) throws GlobalException {
        String name = bindCardDto.getName();
        String cardNo = bindCardDto.getCardNo();
        Validator validator = new Validator();
        validator.add(new BankCardValidationArgs(name, cardNo));
        validator.add(new BankCardValidationUserInfo(user, name));
        validator.add(new ValidationLimit(String.format(RedisKeyHelper.BANK_BIND_LIMIT, user.getId()), redisService));
        validator.valid();

        final List<GlWithdrawUserBankCard> applyCards = cardApplyBusiness.findCardByUserId(user.getId());
        // 检查是否有正在审核中的银行卡
        validator.add(!CollectionUtils.isEmpty(applyCards), "您有正在审批的人工绑定申请，请耐心等待结果。审批完成后可再次申请人工绑定");
        validator.valid();

        // 已绑定的银行卡加上审核中的银行卡
        cardList.addAll(applyCards);
        validator.add(new BankCardValidationCard(user, cardList));
        // 检查银行卡是否已被绑定或已被申请人工绑卡
        validator.add(new BankCardValidationCardExist(cardNo, cardBusiness, cardApplyBusiness));
        validator.valid();
    }

    /**
     * 校验人工绑卡申请
     *
     * @param user
     * @param form
     * @throws GlobalException
     */
    public void validationV2(final GlUserDO user, final ApplyBindCardV2Form form, final List<GlWithdrawUserBankCard> cardList) throws GlobalException {
        String name = form.getName();
        String cardNo = form.getCardNo();
        Validator validator = new Validator();
        // 代理不能申请人工绑卡
        validator.add(new BankCardValidationArgs(name, cardNo));
        validator.add(new BankCardValidationUserInfo(user, name));
        validator.add(new ValidationLimit(String.format(RedisKeyHelper.BANK_BIND_LIMIT, user.getId()), redisService));
        validator.add(new ValidationCode(user, form.getCode(), ProjectConstant.MSG_TYPE_APPLY_CARDBIND, systemApiService)); // 校验验证码
        validator.valid();

        final List<GlWithdrawUserBankCard> applyCards = cardApplyBusiness.findCardByUserId(user.getId());
        // 检查是否有正在审核中的银行卡
        validator.add(!CollectionUtils.isEmpty(applyCards), "您有正在审批的人工绑定申请，请耐心等待结果。审批完成后可再次申请人工绑定");
        validator.valid();

        // 已绑定的银行卡加上审核中的银行卡
        cardList.addAll(applyCards);
        validator.add(new BankCardValidationCard(user, cardList));
        // 检查银行卡是否已被绑定或已被申请人工绑卡
        validator.add(new BankCardValidationCardExist(cardNo, cardBusiness, cardApplyBusiness));
        validator.valid();
    }

    /**
     * 第三方银行卡校验
     * @param user
     * @param name
     * @param cardNo
     * @return
     * @throws GlobalException
     */
    public GlWithdrawUserBankCard doCardValidate(GlUserDO user, String name, String cardNo) throws GlobalException {
        try {
            GlWithdrawUserBankCard userCard = bankCardValidate(user.getId(), name, cardNo);
            Date now = new Date();
            userCard.setCreateDate(now);
            userCard.setLastUpdate(now);
            userCard.setStatus(0);
            userCard.setSelected(0);
            userCard.setUserId(user.getId());
            return userCard;
        }
        catch (GlobalException e) { // 校验不通过异常
            throw e;
        }
        catch (Exception e) {
            log.error("doCardValidate", e);
            throw new GlobalException("获取银行卡查询结果错误");
        }
    }

    /**
     * 校验银行卡
     * @param userId
     * @param name
     * @param cardNo
     * @return
     * @throws GlobalException
     */
    private BankCardBankDto bankCardValidate(Integer userId, String name, String cardNo) throws GlobalException {
        //查看记录表中是否有记录
        BankCardBankDto info = verifyBusiness.findCardVerify(cardNo, name);
        if(!ObjectUtils.isEmpty(info)){
            return info;
        }

        // 调用第三方接口校验
        info = new BankCardBankDto();
        BankCardDto bankCardDto = new BankCardDto();
        bankCardDto.setUserId(userId);
        bankCardDto.setName(name);
        bankCardDto.setCardNo(cardNo);
        RPCResponse<BankCardDto> response = bankCardApiService.bankCardValidate(bankCardDto);
        BankCardDto data = response.getData();
        if(RPCResponseUtils.isFail(response) || ObjectUtils.isEmpty(data) || BooleanUtils.isNotTrue(data.getValidate())) {
            //累加验证错误次数 每天凌晨过期
            String key = RedisKeyHelper.BANK_CARD_VALIDATE_ERROR_COUNT + userId;
            redisService.incrBy(key, 1);
            redisService.setTTL(key, DateUtils.getRemainSecondsOneDay());
            if(ObjectUtils.isEmpty(data) || ObjectUtils.isEmpty(data.getResultCode())){
                log.error("第三方接口校验异常，cardNo:{},name:{}", StringEncryptor.encryptBankCard(cardNo), name);
                throw new GlobalException(response.getMessage());
            }
            else { // 第三方接口校验不通过
                log.error("第三方接口校验不通过，cardNo:{},name:{}", StringEncryptor.encryptBankCard(cardNo), name);
                throw new GlobalException(data.getResultCode());
            }
        }

        // 校验是否支持的银行
        BeanUtils.copyProperties(data, info);
        info.setBankId(bankBusiness.getBankIdByName(info.getBankName()));
        if (ObjectUtils.isEmpty(info.getBankId())) { // 不支持的银行
            log.error("不支持的银行：{}", info.getBankName());
            throw new GlobalException(ResultCode.UNSUPPORTED_BANK_ERROR);
        }

        // 保存校验结果
        info.setCardNo(cardNo);
        info.setName(name);
        verifyBusiness.saveCardVerify(info);

        return info;
    }
}
