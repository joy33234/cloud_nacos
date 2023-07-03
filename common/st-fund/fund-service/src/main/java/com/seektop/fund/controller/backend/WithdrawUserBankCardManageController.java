package com.seektop.fund.controller.backend;

import com.github.pagehelper.PageInfo;
import com.seektop.common.encrypt.annotation.Encrypt;
import com.seektop.common.encrypt.annotation.EncryptField;
import com.seektop.common.encrypt.enums.EncryptTypeEnum;
import com.seektop.common.encrypt.enums.builder.Encryptor;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.BindCardRecordBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.controller.backend.param.bankcard.BindCardForm;
import com.seektop.fund.handler.UserBankCardHandler;
import com.seektop.fund.model.BindCardRecord;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.vo.BindCardRecordForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by CodeGenerator on 2018/03/18.
 */
@RestController
@RequestMapping(value = "/manage/fund/userbankcard", produces = "application/json;charset=utf-8")
public class WithdrawUserBankCardManageController extends FundBackendBaseController {

    @Resource
    private GlWithdrawUserBankCardBusiness glWithdrawUserBankCardService;
    @Autowired
    private UserBankCardHandler userBankCardHandler;
    @Autowired
    private BindCardRecordBusiness bindCardRecordBusiness;
    @Autowired
    private GlWithdrawUserBankCardBusiness bankCardBusiness;

    @PostMapping(value = "/list/user", produces = "application/json;charset=utf-8")
    public Result listUser(@RequestParam Integer userId, @RequestParam(required = false) Integer isNotHide, @RequestParam(required = false)Integer status) {
        return Result.genSuccessResult(this.bankCardList(userId,isNotHide,status));
    }

    /**
     * 查询会员银行卡信息
     * @param userId
     * @return
     */
    @Encrypt(values = {@EncryptField(fieldName = "name", typeEnums = EncryptTypeEnum.NAME),
            @EncryptField(fieldName = "cardNo", typeEnums = EncryptTypeEnum.BANKCARD),
            @EncryptField(fieldName = "address", typeEnums = EncryptTypeEnum.ADDRESS)})
    @PostMapping("/detail/bank")
    public Result bank(@RequestParam Integer userId) throws GlobalException {
        List<GlWithdrawUserBankCard> cardList = bankCardBusiness.getUserCardInfo(userId);
        return Result.genSuccessResult(cardList);
    }

    /**
     * 后台运维人工绑卡
     * @param form
     * @param admin
     * @return
     * @throws Exception
     */
    @RequestMapping("/bindcard")
    public Result bindCard(@Validated BindCardForm form,
                           @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin)
            throws Exception {
        userBankCardHandler.bindCard(form, admin);
        return Result.genSuccessResult();
    }

    /**
     * 后台绑定银行卡操作记录
     * @param form
     * @return
     */
    @RequestMapping("/bindcard/record")
    public Result bindCardRecord(BindCardRecordForm form){
        PageInfo<BindCardRecord> page = bindCardRecordBusiness.findPage(form);
        return Result.genSuccessResult(page);
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
