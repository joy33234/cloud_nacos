package com.seektop.fund.controller.backend;

import com.seektop.common.encrypt.annotation.Encrypt;
import com.seektop.common.encrypt.annotation.EncryptField;
import com.seektop.common.encrypt.enums.EncryptTypeEnum;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.rest.Result;
import com.seektop.fund.business.GlWithdrawUserUsdtAddressBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.vo.UserBindQueryDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/manage/fund/user/bind/")
public class UserBindingManageController extends FundBackendBaseController {

    @Resource
    private GlWithdrawUserBankCardBusiness glWithdrawUserBankCardBusiness;
    @Resource
    private GlWithdrawUserUsdtAddressBusiness glWithdrawUserUsdtAddressBusiness;

    /**
     * 银行卡绑定列表
     */
    @Encrypt(values = {
            @EncryptField(fieldName = "cardNo", typeEnums = EncryptTypeEnum.BANKCARD)
    })
    @PostMapping(value = "bank/list", produces = "application/json;charset=utf-8")
    public Result bankList(UserBindQueryDO queryDO) {
        try{
            return Result.genSuccessResult(glWithdrawUserBankCardBusiness.bankList(queryDO));
        }catch (Exception e){
            log.error("银行卡绑定列表查询失败，e={}",e);
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.BANK_BIND_LIST_GET_ERROR).parse(queryDO.getLanguage()));
        }
    }

    /**
     * USDT 绑定列表
     */
    @Encrypt(values = {
            @EncryptField(fieldName = "address", typeEnums = EncryptTypeEnum.ADDRESS)
    })
    @PostMapping(value = "usdt/list", produces = "application/json;charset=utf-8")
    public Result usdtList(UserBindQueryDO queryDO) {
        try{
            return Result.genSuccessResult(glWithdrawUserUsdtAddressBusiness.usdtList(queryDO));
        }catch (Exception e){
            log.error("USDT 绑定列表查询失败，e={}",e);
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.USDT_BIND_LIST_GET_ERROR).parse(queryDO.getLanguage()));
        }
    }

}
