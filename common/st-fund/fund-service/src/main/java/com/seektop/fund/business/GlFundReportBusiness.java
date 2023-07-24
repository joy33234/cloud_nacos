package com.seektop.fund.business;

import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.dto.ExportFileDto;
import com.seektop.fund.controller.backend.dto.ReportFundsCheckDto;
import com.seektop.fund.controller.backend.param.recharge.FundsCheckReportExclDto;
import com.seektop.fund.handler.ExportFileHandler;
import com.seektop.fund.mapper.GlFundChangeRequestMapper;
import com.seektop.fund.vo.FundsCheckReport;
import com.seektop.fund.vo.FundsExcelReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GlFundReportBusiness {
    @Resource
    private GlFundChangeRequestMapper glFundChangeRequestMapper;
    @Resource
    private DynamicKey dynamicKey;
    @Autowired
    private ExportFileHandler exportFileHandler;

    private static final Integer MAX_EXCEL_REPORT_COUNT = 2000;


    public PageInfo<FundsCheckReport> findFundsCheckReport(Date date, Integer changeType, List<Integer> subType, Integer page, Integer size) {
        List<Integer> subTypeList = new ArrayList<>();
        Date startTime = DateUtils.getStartOfDay(date);
        Date endTime = DateUtils.getEndOfDay(date);
        ;
        if (null != subType && subType.size() > 0) {
            subTypeList.addAll(subType.stream().filter(type -> -1 != type).collect(Collectors.toList()));
        }
        List<FundsCheckReport> fundsCheckReportList = glFundChangeRequestMapper.findFundsCheckReportList(startTime, endTime, changeType, subTypeList);
        return new PageInfo(DtoUtils.transformList(fundsCheckReportList, FundsCheckReport.class));
    }

    public List<FundsExcelReport> findFundsExcelReportList(Date date, Integer changeType, List<Integer> subType) {
        List<Integer> subTypeList = new ArrayList<>();
        int page = 0;
        if (null != subType && subType.size() > 0) {
            subTypeList.addAll(subType.stream().filter(type -> -1 != type).collect(Collectors.toList()));
        }
        Date startTime = DateUtils.getStartOfDay(date);
        Date endTime = DateUtils.getEndOfDay(date);
        ;
        List<FundsExcelReport> resultList = new ArrayList<>();
        // 开始分页处理
        while (true) {
            List<FundsExcelReport> fundsExcelReportList = glFundChangeRequestMapper.findFundsExcelReportList(startTime, endTime, changeType, subTypeList, page, MAX_EXCEL_REPORT_COUNT);
            if (null != fundsExcelReportList) {
                resultList.addAll(fundsExcelReportList);
                if (fundsExcelReportList.size() >= MAX_EXCEL_REPORT_COUNT) {
                    page = (page + 1) * MAX_EXCEL_REPORT_COUNT;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return resultList;
    }

    @Async
    public void export(ReportFundsCheckDto dto, GlAdminDO admin) {
        // 生成Excel文件
        ExportFileDto exportFileDto = new ExportFileDto();
        exportFileDto.setUserId(admin.getUserId());
        exportFileDto.setFileName("资金调整报表");
        exportFileDto.setSupplier(() -> getExportData(dto));
        exportFileHandler.exportFile(exportFileDto);
    }

    private List<FundsCheckReportExclDto> getExportData(ReportFundsCheckDto dto) {
        Date date = dto.getDate();
        Integer changeType = dto.getChangeType();
        List<Integer> subType = dto.getSubType();
        List<FundsExcelReport> fundsCheckReportList = findFundsExcelReportList(date, changeType, subType);
        List<FundsCheckReportExclDto> fundsCheckReportExclDtoList = new ArrayList<>();
        fundsCheckReportList.forEach(item -> {
            FundsCheckReportExclDto exclDto = new FundsCheckReportExclDto();
            if (item.getChangeType().equals(FundConstant.ChangeOperateType.ADD_INCLUDE_PROFIT)) { // 加币-计入红利
                exclDto.setChangeTypeName("加币-计入红利");
            } else if (item.getChangeType().equals(FundConstant.ChangeOperateType.ADD_NOT_INCLUDE_PROFIT)) { // 加币-不计入红利
                exclDto.setChangeTypeName("加币-不计入红利");
            } else if (item.getChangeType().equals(FundConstant.ChangeOperateType.REDUCE)) { // 减币
                exclDto.setChangeTypeName("减币");
            }
            FundConstant.ChangeOperateSubType changeOperateSubType = FundConstant.ChangeOperateSubType.getByValue(dynamicKey.getAppName(), item.getChangeType(),item.getSubType());
            exclDto.setSubTypeName(changeOperateSubType.getName());

            exclDto.setUserName("\t" + exclDto.getUserName() + "\t");
            exclDto.setOrderId(item.getOrderId());
            exclDto.setRelationRechargeOrderId(item.getRelationRechargeOrderId());
            exclDto.setUserTypeName(item.getUserType() == 0 ? "会员" : "代理");
            exclDto.setUserName(item.getUserName());
            exclDto.setAmount(item.getAmount());
            exclDto.setFreezeAmount(item.getFreezeAmount());
            exclDto.setCreator(item.getCreator());
            fundsCheckReportExclDtoList.add(exclDto);
        });
        return fundsCheckReportExclDtoList;
    }
}
