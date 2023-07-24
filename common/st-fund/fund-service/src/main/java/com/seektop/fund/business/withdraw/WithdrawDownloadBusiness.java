package com.seektop.fund.business.withdraw;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.google.common.collect.Lists;
import com.seektop.common.encrypt.enums.builder.Encryptor;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlWithdrawRecordBusiness;
import com.seektop.fund.controller.backend.dto.ExportFileDto;
import com.seektop.fund.controller.backend.dto.ReportCheckDto;
import com.seektop.fund.controller.backend.result.withdraw.GlWithdrawCollectResult;
import com.seektop.fund.controller.backend.result.withdraw.GlWithdrawExportResult;
import com.seektop.fund.handler.ExportFileHandler;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.vo.GlWithdrawDetailDto;
import com.seektop.fund.vo.GlWithdrawExcelDto;
import com.seektop.fund.vo.GlWithdrawQueryDto;
import com.seektop.fund.vo.WithdrawVO;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WithdrawDownloadBusiness {

    @DubboReference(retries = 2, timeout = 3000)
    private GlUserService glUserService;

    @Autowired
    private ExportFileHandler exportFileHandler;

    @Resource
    private GlWithdrawRecordBusiness glWithdrawRecordBusiness;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelService;

    @Resource
    private GlWithdrawMapper glWithdrawMapper;


    //提现记录表头
    private final static String withdrawDownLoadHeaders =
            "提现时间,风控审核时间,提现单号,三方单号,账户类型,用户层级,账户名,会员姓名,收款人姓名,币种,提现金额,手续费,出款金额,汇率,待出款USDT数量,出款时间,出款类型,出款状态,出款商户,商户号,出款银行,出款卡号,出款卡姓名,备注,风控审核人";


    @Async
    public void downLoadList(GlWithdrawQueryDto queryDto, Integer adminUserId) throws GlobalException {
        String exportName = "提现记录" + DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS);
        ExportFileDto exportFileDto = new ExportFileDto();
        exportFileDto.setUserId(adminUserId);
        exportFileDto.setFileName(exportName);
        exportFileDto.setHeaders(withdrawDownLoadHeaders);
        exportFileDto.setSupplier(() -> getExportData(queryDto));
        exportFileHandler.exportFile(exportFileDto);
    }

    private StringBuffer getExportData(GlWithdrawQueryDto queryDto) throws GlobalException {
        int size = 2_000;
        queryDto.setPage(1);
        queryDto.setSize(size);
        GlWithdrawCollectResult<WithdrawVO> result = glWithdrawRecordBusiness.getWithdrawHistoryPageList(queryDto);
        int pages = Math.min(result.getPages(), 100); // 最多导出20万数据
        StringBuffer data = new StringBuffer();
        List<WithdrawVO> list = result.getList();
        for (int i = 0; i < pages; i++) {
            if (i > 0) {
                queryDto.setPage(i + 1);
                result = glWithdrawRecordBusiness.getWithdrawHistoryPageList(queryDto);
                list = result.getList();
            }
            for (WithdrawVO w : list) {
                data.append(getRowData(w)).append("\r\n");
            }
        }
        return data;
    }

    private StringBuffer getRowData(WithdrawVO item) {
        StringBuffer sb = new StringBuffer();
        // 提现时间
        sb.append(DateUtils.format(item.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS)).append(",");
        // 风控审核时间
        sb.append(item.getRiskApvTime() == null ? "" : DateUtils.format(item.getRiskApvTime(), DateUtils.YYYY_MM_DD_HH_MM_SS)).append(",");
        // 提现单号
        sb.append(item.getOrderId()).append(",");
        // 三方单号
        sb.append(item.getThirdOrderId()).append(",");
        // 账户类型
        sb.append(item.getUserType() == UserConstant.UserType.PLAYER ? "会员" : "代理").append(",");
        // 用户层级
        sb.append(item.getUserLevel()).append(",");
        // 账户名
        sb.append("\t").append(item.getUsername()).append("\t").append(",");
        // 会员姓名
        sb.append("\t").append(item.getReallyName().split(",")[0]).append(",");
        // 收款人姓名
        sb.append(StringUtils.isEmpty(item.getName()) ? "" : item.getName().replaceAll("\r|\n", "")).append(",");
        // 币种
        sb.append(item.getCoin()).append(",");
        // 提现金额
        sb.append(item.getAmount()).append(",");
        if (item.getAisleType() != FundConstant.AisleType.C2C &&  //极速提现手续费已返回
                (item.getStatus() == FundConstant.WithdrawStatus.SUCCESS || item.getStatus() == FundConstant.WithdrawStatus.FORCE_SUCCESS)) {
            // 手续费
            sb.append(item.getFee()).append(",");
            // 出款金额
            sb.append(item.getAmount().subtract(item.getFee())).append(",");
            // 汇率
            sb.append(item.getRate()).append(",");
            // 待出款USDT数量
            sb.append(item.getUsdtAmount()).append(",");
            // 出款时间
            sb.append(DateUtils.format(item.getLastUpdate(), DateUtils.YYYY_MM_DD_HH_MM_SS)).append(",");
        } else if (item.getAisleType() == FundConstant.AisleType.C2C &&  //极速提现手续费已返回
                (item.getStatus() == FundConstant.WithdrawStatus.SUCCESS
                        || item.getStatus() == FundConstant.WithdrawStatus.FORCE_SUCCESS
                        || item.getStatus() == FundConstant.WithdrawStatus.RETURN_PART)) {
            // 手续费
            sb.append(item.getFee()).append(",");
            // 出款金额
            sb.append(item.getActualAmount()).append(",");
            // 汇率
            sb.append(item.getRate()).append(",");
            // 待出款USDT数量
            sb.append(item.getUsdtAmount()).append(",");
            // 出款时间
            sb.append(DateUtils.format(item.getLastUpdate(), DateUtils.YYYY_MM_DD_HH_MM_SS)).append(",");
        }else {
            // 手续费
            sb.append("0").append(",");
            // 出款金额
            sb.append("0").append(",");
            // 汇率
            sb.append("0").append(",");
            // 待出款USDT数量
            sb.append("0").append(",");
            // 出款时间
            sb.append(",");
        }
        String withType = "";
        switch (item.getWithdrawType()) {
            case 0:
                withType = "人工打款";
                break;
            case 1:
                withType = "三方自动出款";
                break;
            case 2:
                withType = "三方手动出款";
                break;
        }
        //出款类型
        sb.append(withType).append(",");
        // 出款状态
        String statusName = this.getStatusName(item.getStatus());
        sb.append(StringUtils.isEmpty(statusName) ? "" : statusName).append(",");
        if (item.getWithdrawType() == 0) {
            // 出款商户
            sb.append(",");
            // 商户号
            sb.append(",");
        } else {
            // 出款商户
            sb.append(item.getMerchant() == null ? "" : item.getMerchant()).append(",");
            // 商户号
            sb.append("\t").append(item.getMerchantCode() == null ? "" : item.getMerchantCode()).append(",");
        }
        // 出款银行
        sb.append(item.getTransferBankName() == null ? "" : item.getTransferBankName()).append(",");
        // 出款卡号
        sb.append("\t").append(StringUtils.isEmpty(item.getTransferBankCardNo()) ? "" : item.getTransferBankCardNo()).append(",");
        // 出款卡姓名
        sb.append(StringUtils.isEmpty(item.getTransferName()) ? "" : item.getTransferName()).append(",");
        // 备注
        sb.append("\t").append(StringUtils.isEmpty(item.getRemark()) ? "" : item.getRemark().replaceAll("\r|\n", "")).append(",");
        // 风控审核人
        sb.append("\t").append(item.getRiskApprover()).append("\t");
        return sb;
    }

    public String getStatusName(Integer status) {
        String result = "";
        switch (status) {
            case FundConstant.WithdrawStatus.RISK_PENDING:
                result = "风险待审核";
                break;
            case FundConstant.WithdrawStatus.RISK_REJECT:
                result = "风险审核拒绝";
                break;
            case FundConstant.WithdrawStatus.PENDING:
                result = "风险审核通过-待出款";
                break;
            case FundConstant.WithdrawStatus.SUCCESS:
                result = "出款成功";
                break;
            case FundConstant.WithdrawStatus.FAILED:
                result = "拒绝出款(出款失败)";
                break;
            case FundConstant.WithdrawStatus.RETURN_PENDING:
                result = "申请退回中";
                break;
            case FundConstant.WithdrawStatus.RETURN:
                result = "退回成功";
                break;
            case FundConstant.WithdrawStatus.RETURN_REJECT:
                result = "拒绝退回";
                break;
            case FundConstant.WithdrawStatus.SUCCESS_PENDING:
                result = "申请强制成功中";
                break;
            case FundConstant.WithdrawStatus.AUTO_FAILED:
                result = "自动出款失败";
                break;
            case FundConstant.WithdrawStatus.FORCE_SUCCESS:
                result = "通过强制成功";
                break;
            case FundConstant.WithdrawStatus.FORCE_SUCCESS_REJECT:
                result = "拒绝强制成功";
                break;
            case FundConstant.WithdrawStatus.AUTO_PENDING:
                result = "三方自动出款中";
                break;
            case FundConstant.WithdrawStatus.RETURN_PART_PENDING:
                result = "部分退回中";
                break;
            case FundConstant.WithdrawStatus.RETURN_PART:
                result = "部分退回成功";
                break;
            case FundConstant.WithdrawStatus.RECHARGE_PENDING:
                result = "待付款";
                break;
            case FundConstant.WithdrawStatus.CONFIRM_PENDING:
                result = "待确认到帐";
                break;
            case FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT:
                result = "超时未确认到账";
                break;
        }
        return result;
    }

    public List<GlWithdrawExcelDto> groupResult(List<GlWithdrawExportResult> datas) {
        List<GlWithdrawExcelDto> excelDatas = Lists.newArrayList();
        // 需要处理Excel生成的内容
        for (GlWithdrawExportResult data : datas) {
            GlWithdrawExcelDto dto = new GlWithdrawExcelDto();
            BeanUtils.copyProperties(data, dto);
            switch (data.getWithdrawType()) {
                case 0:
                    dto.setWithdrawTypeName("人工打款");
                    dto.setMerchant(null);
                    dto.setMerchantCode(null);
                    dto.setFee(new BigDecimal(0));
                    break;
                case 1:
                    dto.setWithdrawTypeName("三方自动出款");
                case 2:
                    dto.setWithdrawTypeName("三方手动出款");
                    break;
            }
            dto.setConfirmAmount(data.getAmount().subtract(data.getFee()));//出款確認金額=提現金額-手續費
            dto.setCreateDateStr(DateUtils.format(data.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            dto.setLastUpdateStr(DateUtils.format(data.getLastUpdate(), DateUtils.YYYY_MM_DD_HH_MM_SS));

            dto.setUserName("\t" + dto.getUserName() + "\t");
            dto.setName(data.getName());//收款人姓名
            dto.setUserType(data.getUserType() == 0 ? "会员" : "代理");
            dto.setWithdrawStatus(getStatusName(data.getStatus()));
            dto.setTransferBankCardNo("\t" + Encryptor.builderBankCard().doEncrypt(data.getTransferBankCardNo()));
            dto.setTransferBankName(Encryptor.builderName().doEncrypt(data.getTransferBankName()));
            dto.setTransferName(data.getTransferName());
            dto.setRemark(data.getRemark());
            if (StringUtils.isNotBlank(dto.getMerchantCode())) {
                dto.setMerchantCode("\t" + dto.getMerchantCode());
            }
            excelDatas.add(dto);
        }
        return excelDatas;
    }

    @Async
    public void asyncWithdrawExport(ReportCheckDto dto, GlAdminDO admin) throws GlobalException {
        // 生成Excel文件
        String fileName = "提现报表导出" + DateUtils.format(dto.getDate(), "yyyy-MM-dd");
        ExportFileDto exportFileDto = new ExportFileDto();
        exportFileDto.setUserId(admin.getUserId());
        exportFileDto.setFileName(fileName);
        exportFileDto.setSupplier(() -> getExportData(dto));
        exportFileHandler.exportFile(exportFileDto);
    }

    private List<GlWithdrawExcelDto> getExportData(ReportCheckDto dto) throws GlobalException {
        Date startTime = DateUtils.getStartOfDay(dto.getDate());
        Date endTime = DateUtils.getEndOfDay(dto.getDate());
        String merchantCode = dto.getMerchantCode();
        Integer channelId = dto.getChannelId();
        int size = 2_000;
        Page<Object> page = PageHelper.startPage(1, size);
        // 查询指定时间段内的充值记录
        List<GlWithdrawDetailDto> list = glWithdrawMapper.getWithdrawList(startTime, endTime, merchantCode, channelId,dto.getCoinCode());
        if (list == null || list.size() == 0) {
            log.info("从开始时间：{}到截止时间：{}，查询出来的数据为空，不进行处理", startTime, endTime);
            return Lists.newArrayList();
        }

        int pages = page.getPages();
        int total = Long.valueOf(page.getTotal()).intValue();
        List<GlWithdrawExcelDto> datas = new ArrayList<>(total);
        for (int i = 0; i < pages; i++) {
            if (i > 0) {
                PageHelper.startPage(i + 1, size, false);
                list = glWithdrawMapper.getWithdrawList(startTime, endTime, merchantCode, channelId,dto.getCoinCode());
            }
            List<GlWithdrawDetailDto> finalList = list;
            List<GlWithdrawExcelDto> records = getExportData(dto, finalList);
            datas.addAll(records);
        }
        return datas;
    }

    private List<GlWithdrawExcelDto> getExportData(ReportCheckDto dto, List<GlWithdrawDetailDto> withdraws) throws GlobalException {
        //查询指定时间段内的充值记录
        if (withdraws == null || withdraws.size() == 0) {
            return Lists.newArrayList();
        }
        Integer channelId = dto.getChannelId();
        String merchantCode = dto.getMerchantCode();
        boolean isAll = (channelId == null || channelId == -1 && StringUtils.isEmpty(merchantCode));
        List<Integer> userIds = withdraws.stream().map(GlWithdrawDetailDto::getUserId).distinct().collect(Collectors.toList());
        List<GlUserDO> users = RPCResponseUtils.getData(glUserService.findByIds(userIds));

        List<Integer> leveIds = withdraws.stream()
                .filter(w -> StringUtils.isNotBlank(w.getUserLevel()))
                .map(w -> Integer.parseInt(w.getUserLevel()))
                .distinct().collect(Collectors.toList());
        List<GlFundUserlevel> userlevels = glFundUserlevelService.findByLevelIds(leveIds);

        //填装精装版数据
        List<GlWithdrawExportResult> resultList = Lists.newArrayList();
        for (GlWithdrawDetailDto withdraw : withdraws) {
            if (ObjectUtils.isEmpty(withdraw) || StringUtils.isEmpty(withdraw.getOrderId())) {
                continue;
            }
            GlWithdrawExportResult result = new GlWithdrawExportResult();
            BeanUtils.copyProperties(withdraw, result);
            Optional<GlUserDO> optional = users.stream().filter(u -> u.getId().equals(withdraw.getUserId())).findFirst();
            if (optional.isPresent()) {
                GlUserDO glUser = optional.get();
                result.setReallyName(glUser.getReallyName());
                result.setTelephone(glUser.getTelephone());
                result.setUserType(glUser.getUserType());
            }
            String mcode = "";
            if (!ObjectUtils.isEmpty(withdraw.getMerchantCode())) {
                String[] codes = withdraw.getMerchantCode().split("\\|\\|");
                mcode = codes[0];
            }
            result.setMerchantCode(mcode);
            //查询用户分层表转换用户层级
            Optional<GlFundUserlevel> f = userlevels.stream().filter(l -> result.getUserLevel().equals(l.getLevelId().toString())).findFirst();
            f.ifPresent(l -> result.setUserLevelName(l.getName()));
            resultList.add(result);
        }
        if (isAll) { //导出全部报表
            //全部
            return groupResult(resultList);
        }
        if (channelId == -2) {
            Predicate<? super GlWithdrawExportResult> criteriaByWithdrawTypeManual = withdraw -> withdraw.getWithdrawType().equals(0);
            List<GlWithdrawExportResult> manualWithdraws = resultList.stream().filter(criteriaByWithdrawTypeManual).collect(Collectors.toList());
            //人工出款
            return groupResult(manualWithdraws);
        }

        // 处理自动出款
        Predicate<? super GlWithdrawExportResult> criteriaByWithdrawTypeAuto = withdraw -> withdraw.getWithdrawType().equals(1) || withdraw.getWithdrawType().equals(2);
        List<GlWithdrawExportResult> autoWithdraws = resultList.stream().filter(criteriaByWithdrawTypeAuto).collect(Collectors.toList());
        // 再按照渠道分组
        Predicate<? super GlWithdrawExportResult> criteriaByChannelId = withdraw -> withdraw.getChannelId() != null;
        Map<Integer, List<GlWithdrawExportResult>> channelGroupMap = autoWithdraws.stream().filter(criteriaByChannelId).collect(Collectors.groupingBy(withdraw -> withdraw.getChannelId(), Collectors.toList()));
        List<GlWithdrawExportResult> channelGropList;
        // 再按照商户号分组
        Map<String, List<GlWithdrawExportResult>> merchantGroupMap;
        Predicate<? super GlWithdrawExportResult> criteriaByMerchantCode = withdraw -> withdraw.getChannelId() != null;
        List<GlWithdrawExcelDto> exlResult = Lists.newLinkedList();
        for (Integer cid : channelGroupMap.keySet()) {
            channelGropList = channelGroupMap.get(cid);
            merchantGroupMap = channelGropList.stream().filter(criteriaByMerchantCode).collect(Collectors.groupingBy(withdraw -> withdraw.getMerchantCode(), Collectors.toList()));
            for (String mcode : merchantGroupMap.keySet()) {
                exlResult.addAll(groupResult(merchantGroupMap.get(mcode)));
            }
        }
        return exlResult;
    }
}
