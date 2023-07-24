package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.controller.backend.dto.withdraw.BalanceDto;
import com.seektop.fund.controller.backend.result.GameUserResult;
import com.seektop.gamebet.dto.result.GameUserDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/manage/fund/user/account", method = RequestMethod.POST, produces = "application/json;charset=utf-8")
public class FundUserAccountController extends FundBackendBaseController {

    @Autowired
    private GlFundUserAccountBusiness fundUserAccountBusiness;

    /**
     * 查询会员平台投注输赢及余额
     * 原接口：gl/user/risk/manage/exception/balance
     * @param balanceDto
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/win-and-balance")
    public Result winAndBalance(@Validated BalanceDto balanceDto) throws GlobalException {
        List<GameUserResult> list = fundUserAccountBusiness.getWinAndBalance(balanceDto);
        return Result.genSuccessResult(list);
    }

    /**
     * 查询会员平台余额
     * 原接口：/gl/balance/manage/balance
     * @param balanceDto
     * @return
     */
    @RequestMapping("/balance")
    public Result balance(@Validated BalanceDto balanceDto) throws GlobalException {
        List<GameUserDO> list = fundUserAccountBusiness.getBalance(balanceDto);
        return Result.genSuccessResult(list);
    }
}
