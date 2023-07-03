package com.seektop.fund.controller.backend;

import com.github.pagehelper.PageInfo;
import com.seektop.common.encrypt.annotation.Encrypt;
import com.seektop.common.encrypt.annotation.EncryptField;
import com.seektop.common.encrypt.enums.EncryptTypeEnum;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.rest.Result;
import com.seektop.common.validatorgroup.CommonValidate;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlWithdrawRecordBusiness;
import com.seektop.fund.business.withdraw.GlFundMerchantWithdrawBusiness;
import com.seektop.fund.controller.backend.dto.PageInfoExt;
import com.seektop.fund.controller.backend.param.withdraw.WithdrawBalanceQueryDO;
import com.seektop.fund.controller.backend.result.withdraw.GlWithdrawCollectResult;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.model.GlWithdrawReturnRequest;
import com.seektop.fund.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 新财务系统：提现出款、提现审核、提现分单、提现订单记录列表查询接口
 */

@Slf4j
@RestController
@RequestMapping("/manage/fund/withdraw/record")
public class WithdrawRecordController extends FundBackendBaseController {

    @Resource
    private GlWithdrawRecordBusiness glWithdrawRecordBusiness;

    @Resource
    private GlFundMerchantWithdrawBusiness glFundMerchantWithdrawBusiness;


    /**
     * 提现订单记录列表
     */
    @PostMapping(value = "/list/history", produces = "application/json;charset=utf-8")
    public Result historyList(GlWithdrawQueryDto queryDto) throws GlobalException {
        GlWithdrawCollectResult<WithdrawVO> pageInfo = glWithdrawRecordBusiness.getWithdrawHistoryPageList(queryDto);
        return Result.genSuccessResult(pageInfo);
    }

    /**
     * 会员详情-提现记录
     *
     * @param queryDto
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/member/withdraw", produces = "application/json;charset=utf-8")
    public Result withdraw(GlWithdrawQueryDto queryDto) throws GlobalException {
        if (queryDto.getUserName() == null) {
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.USER_NAME_NOT_EMPTY).parse(queryDto.getLanguage()));
        }
        queryDto.setNeedName(1);
        GlWithdrawCollectResult<WithdrawVO> pageInfo = glWithdrawRecordBusiness.getWithdrawHistoryPageList(queryDto);
        GlWithdrawAllCollect collect = glWithdrawRecordBusiness.getMemberWithdrawTotal(queryDto);
        pageInfo.setGlWithdrawAllCollect(collect);
        return Result.genSuccessResult(pageInfo);
    }

    /**
     * 代理详情-提现订单记录
     * @param queryDto
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/withdraw/list", produces = "application/json;charset=utf-8")
    public Result withdrawList(@Validated(value = CommonValidate.class) GlWithdrawQueryDto queryDto) throws GlobalException {
        queryDto.setDateType(1); //设置体现时间查询
        queryDto.setNeedName(1);
        queryDto.setUserIdList(Collections.singletonList(queryDto.getUserId()));
        GlWithdrawCollectResult<WithdrawVO> pageInfo = glWithdrawRecordBusiness.getWithdrawHistoryPageList(queryDto);
        GlWithdrawAllCollect collect = glWithdrawRecordBusiness.getMemberWithdrawTotal(queryDto);
        pageInfo.setGlWithdrawAllCollect(collect);
        return Result.genSuccessResult(pageInfo);
    }

    /**
     * 代理详情-提现订单记录
     * @param queryDto
     * @return
     */
    @PostMapping("/withdraw/list/export")
    public Result withdrawListExport(@Validated(value = CommonValidate.class) GlWithdrawQueryDto queryDto,
                                     @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin) {
        queryDto.setDateType(1); //设置体现时间查询
        queryDto.setUserIdList(Collections.singletonList(queryDto.getUserId()));
        glWithdrawRecordBusiness.withdrawListExport(queryDto, admin.getUserId());
        return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.DOWNLOADING_CHECK_ON_DOWNLOAD_LIST).parse(queryDto.getLanguage()));
    }

    /**
     * 提现订单记录列表 汇总
     */
    @PostMapping(value = "/list/history/sum", produces = "application/json;charset=utf-8")
    public Result historyListSum(GlWithdrawQueryDto queryDto) throws GlobalException {
        List<GlWithdrawAllCollect> collect = glWithdrawRecordBusiness.getWithdrawCollect(queryDto);
        return Result.newBuilder().success().addData(collect).build();
    }

    /**
     * 提现订单记录导出
     */
    @PostMapping(value = "/list/export", produces = "application/json;charset=utf-8")
    public Result export(GlWithdrawQueryDto queryDto, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        glWithdrawRecordBusiness.export(queryDto, adminDO);
        return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.DOWNLOADING_CHECK_ON_DOWNLOAD_LIST).parse(queryDto.getLanguage()));

    }

    /**
     * 提现出款列表
     */
    @Encrypt(values = {@EncryptField(fieldName = "name", typeEnums = EncryptTypeEnum.NAME),
            @EncryptField(fieldName = "cardNo", typeEnums = EncryptTypeEnum.BANKCARD)})
    @PostMapping(value = "/list/withdraw", produces = "application/json;charset=utf-8")
    public Result withdrawList(WithdrawRecordListQueryDO queryDto) throws GlobalException {
        return Result.genSuccessResult(glWithdrawRecordBusiness.getWithdrawPageList(queryDto));
    }

    /**
     * 提现出款列表汇总
     */
    @PostMapping(value = "/list/withdraw/sum", produces = "application/json;charset=utf-8")
    public Result withdrawListSum(WithdrawRecordListQueryDO queryDto) throws GlobalException {
        return Result.genSuccessResult(glWithdrawRecordBusiness.getWithdrawPageListSum(queryDto));
    }

    /**
     * 提现审核分页查询接口
     *
     * @param queryDto
     * @return
     * @throws GlobalException
     */
    @Encrypt(values = {@EncryptField(fieldName = "reallyName", typeEnums = EncryptTypeEnum.NAME)})
    @PostMapping(value = "/list/approve", produces = "application/json;charset=utf-8")
    public Result approveList(WithdrawApproveListDO queryDto) throws GlobalException {
        PageInfoExt<GlWithdrawReturnRequest> result = glWithdrawRecordBusiness.getWithdrawApprovePageList(queryDto);
        return Result.genSuccessResult(result);
    }
    /**
     * 提现审核待审核统计
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/approve/tips", produces = "application/json;charset=utf-8")
    public Result approveTips() {
        int result = glWithdrawRecordBusiness.getWithdrawApproveTips();
        return Result.genSuccessResult(result);
    }


    /**
     * 提现资金管理列表
     */
    @PostMapping(value = "/list/balance", produces = "application/json;charset=utf-8")
    public Result list(WithdrawBalanceQueryDO queryDO) {
        return Result.genSuccessResult(glFundMerchantWithdrawBusiness.pageList(queryDO));
    }

    /**
     * 提现资金管理金额汇总
     */
    @PostMapping(value = "/total/balance", produces = "application/json;charset=utf-8")
    public Result totalBalance() {
        return Result.genSuccessResult(glFundMerchantWithdrawBusiness.getTotalBalance());
    }
}
