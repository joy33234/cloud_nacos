package com.seektop.fund.controller.forehead;

import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.nacos.adapter.DigitalCoinConfigAdapter;
import com.seektop.common.rest.Result;
import com.seektop.common.utils.RegexValidator;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.adapter.CoinConfigNacosDO;
import com.seektop.enumerate.digital.DigitalProtocolEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlWithdrawUserUsdtAddressBusiness;
import com.seektop.fund.controller.forehead.param.withdraw.UsdtAddDto;
import com.seektop.fund.controller.forehead.param.withdraw.UsdtDeleteDto;
import com.seektop.fund.controller.forehead.result.UsdtResult;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户USDT地址相关接口
 */
@Slf4j
@RestController
@RequestMapping("/forehead/fund/user/usdt")
public class GlUsdtAddressController extends GlFundForeheadBaseController {

    @Resource
    private DigitalCoinConfigAdapter digitalCoinConfigAdapter;
    @Resource
    private GlWithdrawUserUsdtAddressBusiness glWithdrawUserUsdtAddressBusiness;

    /**
     * 查询USDT协议列表
     *
     * @return
     */
    @RequestMapping(value = "/protocol/list", produces = "application/json;charset=utf-8")
    public Result usdtList(ParamBaseDO paramBaseDO) {
        List<UsdtResult> resultList = new ArrayList<>();
        FundConstant.protocolMap.forEach((k, v) -> {
            UsdtResult result = new UsdtResult();
            result.setProtocol(k);
            result.setMessage(LanguageLocalParser.key(FundLanguageDicEnum.USDT_PROTOCOL_TYPE)
                    .withParam(k)
                    .withDefaultValue(v)
                    .parse(paramBaseDO.getLanguage()));
            resultList.add(result);
        });

        return Result.genSuccessResult(resultList);
    }

    /**
     * 查询用户USDT收币地址
     *
     * @param user
     * @return
     */
    @RequestMapping(value = "/list", produces = "application/json;charset=utf-8")
    public Result list(@ModelAttribute(value = "userInfo", binding = false) GlUserDO user) {
        return Result.genSuccessResult(glWithdrawUserUsdtAddressBusiness.findByUserId(user.getId(), 0));
    }

    /**
     * 新增USDT地址
     *
     * @param usdtAddDto
     * @param user
     * @return
     */
    @RequestMapping(value = "/add", produces = "application/json;charset=utf-8")
    public Result add(@Validated UsdtAddDto usdtAddDto, @ModelAttribute(value = "userInfo", binding = false) GlUserDO user) throws GlobalException {
        Result result = Result.genFailResult("");
        if (usdtAddDto.getNickName().length() > 15) {
            result.setMessage("钱包别名长度超限,请重新输入");
            result.setKeyConfig(FundLanguageMvcEnum.WALLET_NICKNAME_TOO_LONG);
            return result;
        }
        // 检查币种编码是否存在
        if (digitalCoinConfigAdapter.isExist(usdtAddDto.getCoin()) == false) {
            result.setMessage("币种不存在,核对后重新输入");
            result.setKeyConfig(FundLanguageMvcEnum.WALLET_COIN_NOT_EXIST);
            return result;
        }
        // 检查币种协议是否支持
        CoinConfigNacosDO coinConfigNacosDO = digitalCoinConfigAdapter.getCoinConfig(usdtAddDto.getCoin());
        if (coinConfigNacosDO.getProtocols().contains(usdtAddDto.getProtocol()) == false) {
            result.setMessage("协议不存在,核对后重新输入");
            result.setKeyConfig(FundLanguageMvcEnum.WALLET_PROTOCOL_NOT_EXIST);
            return result;
        }
        // 校验地址格式
        DigitalProtocolEnum protocolEnum = DigitalProtocolEnum.getDigitalProtocol(usdtAddDto.getProtocol());
        if (StringUtils.hasText(protocolEnum.getRegex()) && Pattern.matches(protocolEnum.getRegex(), usdtAddDto.getAddress()) == false) {
            result.setMessage("地址格式错误,核对后重新输入");
            result.setKeyConfig(FundLanguageMvcEnum.WALLET_ADDRESS_FORMAT_ERROR);
            return result;
        }
        // 检查是否存在
        if (glWithdrawUserUsdtAddressBusiness.isExist(usdtAddDto.getCoin(), usdtAddDto.getProtocol(), usdtAddDto.getAddress())) {
            result.setMessage("地址已存在");
            result.setKeyConfig(FundLanguageMvcEnum.WALLET_ADDRESS_EXIST);
            return result;
        }
        // 检查是否超过绑定数量
        if (glWithdrawUserUsdtAddressBusiness.addressCount(user.getId()) >= 5) {
            result.setMessage("地址最多添加5个");
            result.setKeyConfig(FundLanguageMvcEnum.WALLET_ADDRESSES_MAX_FIVE);
            return result;
        }
        glWithdrawUserUsdtAddressBusiness.add(usdtAddDto, user);
        return Result.genSuccessResult();
    }

    /**
     * 删除USDT地址
     *
     * @param usdtDeleteDto
     * @param user
     * @return
     */
    @RequestMapping(value = "/del", produces = "application/json;charset=utf-8")
    public Result delete(@Validated UsdtDeleteDto usdtDeleteDto, @ModelAttribute(value = "userInfo", binding = false) GlUserDO user) throws GlobalException {
        glWithdrawUserUsdtAddressBusiness.delete(usdtDeleteDto, user);
        return Result.genSuccessResult();
    }
}
