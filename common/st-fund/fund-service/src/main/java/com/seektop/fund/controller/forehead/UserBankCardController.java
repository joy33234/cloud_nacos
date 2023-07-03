package com.seektop.fund.controller.forehead;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBankBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.controller.backend.result.UserBankCardListResult;
import com.seektop.fund.controller.forehead.param.userCard.*;
import com.seektop.fund.handler.UserBankCardHandler;
import com.seektop.fund.model.GlWithdrawBank;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户银行卡
 */
@RestController
@RequestMapping(value = "/forehead/fund/userbankcard", method = RequestMethod.POST, produces = "application/json;charset=utf-8")
public class UserBankCardController extends GlFundForeheadBaseController {

    @Autowired
    private GlWithdrawUserBankCardBusiness bankCardBusiness;
    @Autowired
    private GlWithdrawBankBusiness bankBusiness;
    @Autowired
    private UserBankCardHandler userBankCardHandler;

    /**
     * 查询自己已绑定的银行卡
     * @return
     */
    @RequestMapping("/list")
    public Result list(@ModelAttribute(value = "userInfo", binding = false) GlUserDO user) {
        UserBankCardListResult result = bankCardBusiness.getUserBankCardInfo(user);
        return Result.newBuilder().success().addData(result).build();
    }

    /**
     * 查询支持银行列表
     * @return
     */
    @RequestMapping("/supportBankList")
    public Result supportBankList() {
        List<GlWithdrawBank> list = bankBusiness.findAll();
        return Result.newBuilder().success().addData(list).build();
    }

    /**
     * 用户绑定银行卡
     * @param bindCardDto
     * @return
     */
    @RequestMapping("/bindcard")
    public Result bindCard(@Validated BindCardDto bindCardDto, @ModelAttribute(value = "userInfo", binding = false) GlUserDO user) throws GlobalException {
        GlWithdrawUserBankCard card = userBankCardHandler.bindCard(bindCardDto, user);
        return Result.newBuilder().success().addData(card).build();
    }

    /**
     * 用户绑定银行卡(需要短信验证码)
     * @param form
     * @return
     */
    @RequestMapping("/v2/bindcard")
    public Result bindCardByCode(@Validated BindCardV2Form form, @ModelAttribute(value = "userInfo", binding = false) GlUserDO user) throws GlobalException {
        GlWithdrawUserBankCard card = userBankCardHandler.bindCardV2(form, user);
        return Result.newBuilder().success().addData(card).build();
    }

    /**
     * 用户申请人工绑定银行卡
     * @param bindCardDto
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/apply/bindcard")
    public Result applyBindCard(@Validated ApplyBindCardDto bindCardDto, @ModelAttribute(value = "userInfo", binding = false) GlUserDO user) throws GlobalException {
        userBankCardHandler.applyBindCard(bindCardDto, user);
        return Result.newBuilder().success().build();
    }

    /**
     * 用户申请人工绑定银行卡(需要短信验证码)
     * @param form
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/v2/apply/bindcard")
    public Result applyBindCardByCode(@Validated ApplyBindCardV2Form form, @ModelAttribute(value = "userInfo", binding = false) GlUserDO user) throws GlobalException {
        userBankCardHandler.applyBindCardV2(form, user);
        return Result.newBuilder().success().build();
    }

    /**
     * 删除银行卡
     * @param deleteCardDto
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/delete")
    public Result delete(@Validated DeleteCardDto deleteCardDto, @ModelAttribute(value = "userInfo", binding = false) GlUserDO user) throws GlobalException {
        userBankCardHandler.deleteCard(deleteCardDto, user);
        return Result.newBuilder().success().build();
    }

    /**
     * 用户修改银行卡选中状态
     * @param userDO
     * @param cardId
     * @return
     */
    @RequestMapping("/update")
    public Result update(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, @RequestParam Integer cardId) throws GlobalException {
        userBankCardHandler.updateCardSelect(userDO, cardId);
        return Result.newBuilder().success().build();
    }
}
