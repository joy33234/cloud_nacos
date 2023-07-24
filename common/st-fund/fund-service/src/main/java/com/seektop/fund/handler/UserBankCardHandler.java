package com.seektop.fund.handler;

import com.alibaba.fastjson.JSONArray;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.BindCardRecordBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.business.withdraw.WithdrawUserBankCardApplyBusiness;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.dto.BankCardOperatingDto;
import com.seektop.fund.controller.backend.param.bankcard.BindCardForm;
import com.seektop.fund.controller.forehead.param.userCard.*;
import com.seektop.fund.handler.validation.Validator;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class UserBankCardHandler {

    @Resource
    private UserVipUtils userVipUtils;
    @Resource
    private DynamicKey dynamicKey;

    @Reference(retries = 2, timeout = 3000)
    private GlUserService userService;
    @Autowired
    private GlWithdrawUserBankCardBusiness cardBusiness;
    @Autowired
    private WithdrawUserBankCardApplyBusiness cardApplyBusiness;
    @Autowired
    private BankCardValidateHandler bankCardValidateHandler;
    @Autowired
    private BindCardRecordBusiness bindCardRecordBusiness;

    /**
     * 用户绑定银行卡处理
     * @param bindCardDto
     * @throws GlobalException
     */
    public GlWithdrawUserBankCard bindCard(BindCardDto bindCardDto, GlUserDO user) throws GlobalException {
        user = RPCResponseUtils.getData(userService.findById(user.getId()));
        // 银行卡校验
        List<GlWithdrawUserBankCard> cardList = cardBusiness.findUserActiveCardList(user.getId());
        String name = getName(bindCardDto.getName(), cardList.size(), user);
        String cardNo = getCardNo(bindCardDto.getCardNo());
        bindCardDto.setName(name);
        bindCardDto.setCardNo(cardNo);
        // 数据校验
        bankCardValidateHandler.validation(bindCardDto, user);
        // 银行卡校验
        bankCardValidateHandler.validation(user, cardNo, cardList);
        // 第三方校验
        GlWithdrawUserBankCard bankCard = bankCardValidateHandler.doCardValidate(user, name, cardNo);
        // 绑定银行卡
        BankCardOperatingDto dto = new BankCardOperatingDto();
        BeanUtils.copyProperties(bindCardDto, dto);
        dto.setOperationType(UserConstant.UserOperateType.BIND_BANK_CARD_OPTTYPE);
        dto.setBankCard(bankCard);
        dto.setCardList(cardList);
        dto.setUser(user);
        try {
            cardBusiness.operatingBankCard(dto);
            bankCard.setCardNo(StringEncryptor.encryptBankCard(bankCard.getCardNo()));
            bankCard.setName(StringEncryptor.encryptUsername(bankCard.getName()));
        } catch (Exception e) {
            log.error("cardBusiness.operatingBankCard error", e);
            throw new GlobalException(ResultCode.CARD_VALIDATE_ERROR);
        }
        return bankCard;
    }

    /**
     * 用户绑定银行卡处理
     * @param form
     * @throws GlobalException
     */
    public GlWithdrawUserBankCard bindCardV2(BindCardV2Form form, GlUserDO user) throws GlobalException {
        user = RPCResponseUtils.getData(userService.findById(user.getId()));
        // 银行卡校验
        List<GlWithdrawUserBankCard> cardList = cardBusiness.findUserActiveCardList(user.getId());
        String name = getName(form.getName(), cardList.size(), user);
        String cardNo = getCardNo(form.getCardNo());
        form.setName(name);
        form.setCardNo(cardNo);
        // 数据校验
        bankCardValidateHandler.validationV2(form, user);
        // 银行卡校验
        bankCardValidateHandler.validation(user, cardNo, cardList);
        // 第三方校验
        GlWithdrawUserBankCard bankCard = bankCardValidateHandler.doCardValidate(user, name, cardNo);
        // 绑定银行卡
        BankCardOperatingDto dto = new BankCardOperatingDto();
        BeanUtils.copyProperties(form, dto);
        dto.setOperationType(UserConstant.UserOperateType.BIND_BANK_CARD_OPTTYPE);
        dto.setBankCard(bankCard);
        dto.setCardList(cardList);
        dto.setUser(user);
        try {
            cardBusiness.operatingBankCard(dto);
            bankCard.setCardNo(StringEncryptor.encryptBankCard(bankCard.getCardNo()));
            bankCard.setName(StringEncryptor.encryptUsername(bankCard.getName()));
        } catch (Exception e) {
            log.error("cardBusiness.operatingBankCard error", e);
            throw new GlobalException(ResultCode.CARD_VALIDATE_ERROR);
        }
        return bankCard;
    }

    /**
     * 后端运维人工绑卡
     * @param form
     * @param admin
     */
    public void bindCard(BindCardForm form, GlAdminDO admin) throws Exception {
        RPCResponse<GlUserDO> rpcResponse = userService.findByUserName(StringUtils.trim(form.getUsername()));
        Validator.build().add(RPCResponseUtils.isFail(rpcResponse), "账号错误或不存在").valid();
        GlUserDO user = rpcResponse.getData();
        String name = StringUtils.trim(form.getName());
        String cardNo = getCardNo(form.getCardNo());
        form.setName(name);
        form.setCardNo(cardNo);
        // 数据校验
        bankCardValidateHandler.validation(form, user, admin);
        // 银行卡校验
        List<GlWithdrawUserBankCard> cardList = cardBusiness.findUserActiveCardList(user.getId());
        bankCardValidateHandler.validation(user, cardNo, cardList);
        // 绑定银行卡
        GlWithdrawUserBankCard bankCard = new GlWithdrawUserBankCard();
        Date now = new Date();
        bankCard.setCreateDate(now);
        bankCard.setLastUpdate(now);
        bankCard.setStatus(0);
        bankCard.setSelected(0);
        bankCard.setUserId(user.getId());
        bankCard.setCardNo(cardNo);
        bankCard.setName(name);
        bankCard.setBankId(form.getBankId());
        bankCard.setBankName(form.getBankName());
        bankCard.setAddress("");

        BankCardOperatingDto dto = new BankCardOperatingDto();
        dto.setOperationType(UserConstant.UserOperateType.BIND_BANK_CARD_OPTTYPE);
        dto.setBankCard(bankCard);
        dto.setCardList(cardList);
        dto.setUser(user);
        dto.setUserOperating(false); // 非用户操作
        try {
            cardBusiness.operatingBankCard(dto);
            // 绑定银行卡记录
            bindCardRecordBusiness.insert(bankCard, user, admin);
        } catch (Exception e) {
            log.error("cardBusiness.operatingBankCard error", e);
            throw new GlobalException(ResultCode.CARD_VALIDATE_ERROR);
        }
    }

    /**
     * 申请人工绑定银行卡
     * @param bindCardDto
     * @throws GlobalException
     */
    public void applyBindCard(ApplyBindCardDto bindCardDto, GlUserDO user) throws GlobalException {
        RPCResponse<GlUserDO> response = userService.findById(user.getId());
        user = RPCResponseUtils.getData(response);
        List<GlWithdrawUserBankCard> cardList = cardBusiness.findUserActiveCardList(user.getId());
        bindCardDto.setName(getName(bindCardDto.getName(), cardList.size(), user));
        bindCardDto.setCardNo(getCardNo(bindCardDto.getCardNo()));
        // 校验数据
        bankCardValidateHandler.validation(user, bindCardDto, cardList);
        // 保存人工绑卡申请
        cardApplyBusiness.saveApplyBindCard(user, bindCardDto);
    }

    /**
     * 申请人工绑定银行卡
     * @param form
     * @throws GlobalException
     */
    public void applyBindCardV2(ApplyBindCardV2Form form, GlUserDO user) throws GlobalException {
        RPCResponse<GlUserDO> response = userService.findById(user.getId());
        user = RPCResponseUtils.getData(response);
        List<GlWithdrawUserBankCard> cardList = cardBusiness.findUserActiveCardList(user.getId());
        form.setName(getName(form.getName(), cardList.size(), user));
        form.setCardNo(getCardNo(form.getCardNo()));
        // 校验数据
        bankCardValidateHandler.validationV2(user, form, cardList);
        // 保存人工绑卡申请
        cardApplyBusiness.saveApplyBindCard(user, form);
    }

    /**
     * 删除银行卡
     * @param dto
     * @throws GlobalException
     */
    public void deleteCard(DeleteCardDto dto, GlUserDO user) throws GlobalException {
        GlWithdrawUserBankCard dbCard = cardBusiness.findById(dto.getCardId());
        // 如果是会员用户删除银行卡需要做特殊校验
        if (UserTypeEnum.MEMBER.code() == user.getUserType()) {
            // 检查用户的VIP等级是否可以删除
            UserVIPCache vipCache = userVipUtils.getUserVIPCacheFromDB(user.getId());
            JSONArray vipLevelConfigArray = dynamicKey.getDynamicValue(DynamicKey.Key.USER_BANKCARD_DELETE_VIP_LEVEL, JSONArray.class);
            if (vipLevelConfigArray.contains(vipCache.getVipLevel()) == false) {
                dbCard = null;
            }
            // 检查是否是用户的最后一张卡
            List<GlWithdrawUserBankCard> cardList = cardBusiness.findUserActiveCardList(user.getId());
            if (cardList.size() <= 1) {
                dbCard = null;
            }
        }
        bankCardValidateHandler.validation(0, user, dbCard, dto.getCode());

        Date now = new Date();
        GlWithdrawUserBankCard card = new GlWithdrawUserBankCard();
        card.setCardId(dbCard.getCardId());
        card.setName(dbCard.getName());
        card.setCardNo(dbCard.getCardNo());
        card.setStatus(1);
        card.setLastUpdate(now);

        List<GlWithdrawUserBankCard> cardList = new ArrayList<>();
        //如果是代理的话需要查出所有银行卡列表更新代理真实姓名
        if (user.getUserType() == UserConstant.Type.PROXY) {
            cardList = cardBusiness.findUserActiveCardList(user.getId());
        }

        // 解绑银行卡
        BankCardOperatingDto operatingDto = new BankCardOperatingDto();
        BeanUtils.copyProperties(dto, operatingDto);
        operatingDto.setOperationType(UserConstant.UserOperateType.UNTIED_BANK_CARD_OPTTYPE);
        operatingDto.setBankCard(card);
        operatingDto.setCardList(cardList);
        operatingDto.setUser(user);
        cardBusiness.operatingBankCard(operatingDto);
    }

    /**
     * 用户修改银行卡选中状态
     * @param glUser
     * @param cardId
     * @throws GlobalException
     */
    public void updateCardSelect(GlUserDO glUser, Integer cardId) throws GlobalException {
        GlWithdrawUserBankCard userBankCard = cardBusiness.findById(cardId);
        bankCardValidateHandler.validation(1, glUser, userBankCard, "");
        cardBusiness.doUserCardSelect(userBankCard);
    }

    /**
     * 银行卡姓名处理
     * @param name
     * @param bindCount
     * @param user
     * @return
     * @throws GlobalException
     */
    private String getName(String name, int bindCount, GlUserDO user) throws GlobalException {
        if (user.getUserType() == UserConstant.Type.PLAYER) {
            if (bindCount > 0 && StringUtils.isBlank(user.getReallyName())) {
                log.error("user.reallyName不存在，userId:{}", user.getId());
                throw new GlobalException("用户真实姓名不存在，请联系客服人员");
            }
            if (StringUtils.isNotBlank(user.getReallyName())) {
                name = user.getReallyName();
            }
        }
        return StringUtils.trim(name);
    }

    /**
     * 银行卡号处理
     * @param cardNo
     * @return
     */
    private String getCardNo(String cardNo) {
        return RegExUtils.replaceAll(cardNo, "\\s|\\n|\\r", "");
    }
}
