package com.seektop.fund.business.recharge;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.google.common.collect.Lists;
import com.seektop.common.encrypt.EncryptHelper;
import com.seektop.common.mongo.file.MongoFileDO;
import com.seektop.common.mongo.file.MongoFileService;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.controller.backend.dto.ExportFileDto;
import com.seektop.fund.controller.backend.dto.GlRechargeExcelDto;
import com.seektop.fund.controller.backend.dto.ReportCheckDto;
import com.seektop.fund.controller.backend.result.recharge.GlRechargeCollectResult;
import com.seektop.fund.controller.backend.result.recharge.RechargeExportResult;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.handler.ExportFileHandler;
import com.seektop.fund.handler.RechargeRecordHandler;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.vo.RechargeQueryDto;
import com.seektop.system.dto.param.GlExportCompleteDO;
import com.seektop.system.service.GlExportService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RechargeExportBusiness {

    @DubboReference(retries = 2, timeout = 5000)
    private GlExportService glExportService;

    @DubboReference(retries = 2, timeout = 5000)
    private GlUserService glUserService;

    @Resource
    private MongoFileService fileService;

    @Autowired
    private MongoFileService mongoFileService;

    @Resource
    private GlRechargePayBusiness glRechargePayBusiness;

    @Resource
    private GlRechargeSuccessApproveBusiness glRechargeSuccessApproveBusiness;

    @Resource
    private GlRechargeSuccessRequestBusiness glRechargeSuccessRequestBusiness;

    @Resource
    private GlRechargeRelationBusiness glRechargeRelationBusiness;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Resource
    private GlRechargeMapper glRechargeMapper;

    @Resource
    private RechargeRecordHandler rechargeRecordHandler;

    @Autowired
    private ExportFileHandler exportFileHandler;

    @Async
    public void downLoadList(RechargeQueryDto queryDto, GlAdminDO admin) throws GlobalException {
        Integer adminUserId = admin.getUserId();
        Long startTime = System.currentTimeMillis();
        log.info("downLoadList_start:{}", startTime);
        Date nowDate = new Date();
        queryDto.setSize(2000);
        GlRechargeCollectResult<GlRechargeDO> preInfo = rechargeRecordHandler.findRechargeRecordPageList(queryDto);
        long totalPage = preInfo.getPages();
        long alreadyCount = 0;
        int times = 1;
        List<GlRechargeDO> list = new ArrayList<>();
        while (queryDto.getPage() <= totalPage) {
            GlRechargeCollectResult<GlRechargeDO> pageList = EncryptHelper.startEncrypt(() -> rechargeRecordHandler.findRechargeRecordPageList(queryDto), adminUserId);
            queryDto.setPage(queryDto.getPage() + 1);
            list.addAll(pageList.getList());
            alreadyCount = alreadyCount + pageList.getList().size();
            if (alreadyCount >= 100000) {
                writExportToMongo(list, adminUserId, "充值记录" + DateUtils.format(nowDate, DateUtils.YYYYMMDDHHMMSS) + "-" + times);
                alreadyCount = 0;
                list = new ArrayList<>();
                times = times + 1;
            }
            if (queryDto.getPage() > totalPage) {
                writExportToMongo(list, adminUserId, "充值记录" + DateUtils.format(nowDate, DateUtils.YYYYMMDDHHMMSS) + "-" + times);
                break;
            }
        }
        Long endTime = System.currentTimeMillis();
        log.info("downLoadList_end:{}", endTime);
        log.info("downLoadList_costTime:{}", endTime - startTime);
    }

    protected void writExportToMongo(List<GlRechargeDO> list, Integer userId, String title) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        log.info("开始处理{}", title);
        // 生成一个待处理的文件
        Integer exportId = glExportService.startExport(userId, title);
        GlExportCompleteDO completeDO = new GlExportCompleteDO();
        completeDO.setIsBackend(true);
        completeDO.setCompress(false);
        completeDO.setExportId(exportId);
        try {
            // 组装导出文件的数据
            byte[] data = getRechargeDownLoadData(list);
            if (data == null || data.length <= 0) {
                return;
            }
            MongoFileDO mongoFileDO = new MongoFileDO();
            mongoFileDO.setContentType("application/csv");
            mongoFileDO.setData(data);
            mongoFileDO.setFileName(UUID.randomUUID().toString() + ".csv");
            String fileId = fileService.save(mongoFileDO);
            completeDO.setMongoFileId(fileId);
            completeDO.setStatus(1);
        } catch (Exception ex) {
            log.error("处理{}发生异常", title, ex);
            completeDO.setStatus(2);
        } finally {
            glExportService.completeExport(completeDO);
        }
    }

    private final static String rechargeDownLoadHeaders = "充值时间,充值单号,三方单号,账户类型,用户层级,账户名,会员姓名,付款人姓名,币种,存款金额,到账金额,到账时间,充值方式,支付状态,存款商户,商户号/收款用户名,补单审核时间,收款银行,收款人姓名,银行卡号,订单汇率,实际汇率,提单USDT,实际支付USDT,钱包地址";

    private byte[] getRechargeDownLoadData(List<GlRechargeDO> data) throws GlobalException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // BOM标识
            outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            // 写表头
            outputStream.write(rechargeDownLoadHeaders.getBytes(StandardCharsets.UTF_8));
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            StringBuffer sb;
            for (GlRechargeDO item : data) {
                sb = new StringBuffer();
                // 充值时间
                sb.append(DateUtils.format(item.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
                sb.append(",");
                // 充值单号
                sb.append(item.getOrderId());
                sb.append(",");
                // 三方单号
                sb.append(item.getThirdOrderId());
                sb.append(",");
                // 账户类型
                switch (item.getUserType()) {
                    case 0:
                        sb.append("会员");
                        break;
                    case 1:
                        sb.append("代理");
                }
                sb.append(",");

                // 用户层级
                sb.append(item.getUserLevel());
                sb.append(",");
                // 账户名
                sb.append("\t").append(item.getUsername()).append("\t");
                sb.append(",");
                // 会员姓名
                sb.append("\t").append(item.getReallyName().split(",")[0]).append(",");
                // 付款人姓名
                if (StringUtils.isNotEmpty(item.getKeyword()) && item.getKeyword().contains("||")) {
                    String[] keywordArray = item.getKeyword().split("\\|\\|");
                    if (keywordArray.length > 0) {
                        sb.append(keywordArray[0].replaceAll("\r|\n", ""));
                    } else {
                        sb.append("无");
                    }
                } else {
                    sb.append("无");
                }
                sb.append(",");
                // 币种
                sb.append(item.getCoin());
                sb.append(",");
                // 存款金额
                sb.append(item.getAmount());
                sb.append(",");
                // 到账金额
                sb.append(item.getPayAmount() != null ? item.getPayAmount() : BigDecimal.ZERO);
                sb.append(",");
                // 到账时间
                sb.append(item.getPayTime() != null ? DateUtils.format(item.getPayTime(), DateUtils.YYYY_MM_DD_HH_MM_SS) : "-");
                sb.append(",");
                // 充值类型
                sb.append(FundConstant.paymentTypeMap.get(item.getPaymentId()));
                sb.append(",");
                // 支付状态
                sb.append(this.getRechargeStatus(item.getStatus(), item.getSubStatus()));
                sb.append(",");
                // 存款商户
                sb.append(item.getMerchantName());
                sb.append(",");
                // 商户号
                sb.append("\t").append(item.getMerchantCode());
                sb.append(",");
                // 补单审核时间
                sb.append(item.getSucApvTime() != null ? DateUtils.format(item.getSucApvTime(), DateUtils.YYYY_MM_DD_HH_MM_SS) : "-");
                sb.append(",");
                // 收款银行
                sb.append(item.getBankName());
                sb.append(",");
                // 收款人姓名
                sb.append(StringUtils.isNotEmpty(item.getCardUsername()) ? item.getCardUsername() : "");
                sb.append(",");
                // 银行卡号
                sb.append("\t").append(StringUtils.isNotEmpty(item.getCardNo()) ? item.getCardNo() : "");
                sb.append(",");
                //USDT订单汇率
                sb.append(item.getRate() != null ? item.getRate() : BigDecimal.ZERO);
                sb.append(",");
                //USDT实际汇率
                sb.append(item.getPayRate() != null ? item.getPayRate() : BigDecimal.ZERO);
                sb.append(",");
                //USDT数量
                sb.append(item.getUsdtAmount() != null ? item.getUsdtAmount() : BigDecimal.ZERO);
                sb.append(",");
                //USDT支付数量
                sb.append(item.getUsdtPayAmount() != null ? item.getUsdtPayAmount() : BigDecimal.ZERO);
                sb.append(",");
                //收款钱包地址
                sb.append("\t").append(item.getBlockAddress() != null ? item.getBlockAddress() : "");

                outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            log.error("getRechargeDownLoadData error", ex);
            throw new GlobalException("getRechargeDownLoadData error", ex);
        }
    }

    public String getRechargeStatus(Integer status, Integer subStatus) {
        String statusName = null;
        switch (status) {
            case 0:
                statusName = "待支付";
                if (subStatus != null && subStatus == 7) {
                    statusName = "待确认到帐";
                } else if (subStatus != null && subStatus == 8) {
                    statusName = "超时待确认到账";
                }
                break;
            case 1:
                if (subStatus != null && subStatus == 1) {
                    statusName = "支付成功";
                } else if (subStatus != null && subStatus == 2) {
                    statusName = "补单审核成功";
                } else {
                    statusName = "支付成功";
                }
                break;
            case 2:
                if (subStatus != null && subStatus == 6) {
                    statusName = "超时撤销";
                } else if (subStatus != null && subStatus == 5) {
                    statusName = "用户撤销";
                } else if (subStatus != null && subStatus == 4) {
                    statusName = "人工拒绝补单";
                } else if (subStatus != null && subStatus == 3) {
                    statusName = "补单审核拒绝";
                } else {
                    statusName = "支付失败";
                }
                break;
            case 3:
                statusName = "补单审核中";
                break;
            default:
                statusName = status + "-" + subStatus;
                break;
        }
        return statusName;
    }

    public List<GlRecharge> exportRecharge(Date startTime, Date endTime, Integer merchant, String merchantCode, Integer channelId,String coin) {
        List<GlRecharge> recharges = glRechargeMapper.findRechargeList(startTime, endTime, merchant, merchantCode, channelId,coin);
        if (recharges == null || recharges.size() == 0) {
            log.info("从开始时间：{}到截止时间：{}，查询出来的数据为空，不进行处理", startTime, endTime);
            return Lists.newArrayList();
        }
        return recharges;
    }

    public Collection<? extends GlRechargeExcelDto> convertRechargeData(Integer channelId, String merchantCode, List<RechargeExportResult> datas) {
        List<GlRechargeExcelDto> excelDatas = Lists.newArrayList();
        GlRechargeExcelDto dto = null;
        // 需要处理Excel生成的内容
        for (RechargeExportResult data : datas) {
            dto = new GlRechargeExcelDto();
            BeanUtils.copyProperties(data, dto);
            switch (data.getUserType()) {
                case 0:
                    dto.setUserTypeName("会员");
                    break;
                case 1:
                    dto.setUserTypeName("代理");
                    break;
            }
            switch (data.getLimitType()) {
                case 0:
                    dto.setLimitTypeName("普通存款");
                    break;
                case 1:
                    dto.setLimitTypeName("大额存款");
                    break;
            }

            dto.setUsername("\t" + dto.getUsername() + "\t");
            dto.setStatusName(getStatusName(data.getStatus()));
            dto.setClientTypeName(getClientTypeName(data.getClientType()));//客户端名称
            dto.setCreateDateStr(DateUtils.format(data.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
//                    dto.setLastUpdateStr(DateUtils.format(data.getLastUpdate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            dto.setPayTimeStr(DateUtils.format(data.getPayTime(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            dto.setSucApvTimeStr(DateUtils.format(data.getSucApvTime(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            dto.setBankName(data.getBankName());
            dto.setCardUserName(data.getCardUsername() == null ? "" : data.getCardUsername());
            dto.setCardNo(data.getCardNo() == null ? "" : data.getCardNo() + "\t");
            dto.setKeyword(data.getKeyword() == null ? "" : data.getKeyword());
            excelDatas.add(dto);
        }
        return excelDatas;
    }

    public String getStatusName(Integer status) {
        String result = null;
        if (status == null) {
            return result;
        }
        switch (status) {
            case 0:
                result = "待支付";
                break;
            case 1:
                result = "支付成功";
                break;
            case 2:
                result = "支付失败";
                break;
            case 3:
                result = "补单审核中";
                break;
        }
        return result;
    }

    public String getClientTypeName(Integer clientType) {
        String result = null;
        if (clientType == null) {
            return result;
        }
        switch (clientType) {
            case 0:
                result = "PC端";
                break;
            case 1:
                result = "H5";
                break;
            case 2:
                result = "安卓";
                break;
            case 3:
                result = "IOS";
                break;
            case 4:
                result = "PAD";
                break;
        }
        return result;
    }

    @Async
    public void export(ReportCheckDto dto, GlAdminDO admin) {
        // 生成Excel文件
        String fileName = "充值报表导出" + DateUtils.format(dto.getDate(), "yyyy-MM-dd");
        ExportFileDto exportFileDto = new ExportFileDto();
        exportFileDto.setUserId(admin.getUserId());
        exportFileDto.setFileName(fileName);
        exportFileDto.setSupplier(() -> getExportData(dto));
        exportFileHandler.exportFile(exportFileDto);
    }

    public List<GlRechargeExcelDto> getExportData(ReportCheckDto dto) throws GlobalException {
        int size = 2_000;
        Date start = DateUtils.getStartOfDay(dto.getDate());
        Date end = DateUtils.getEndOfDay(dto.getDate());
        String merchantCode = dto.getMerchantCode();
        Integer channelId = dto.getChannelId();
        boolean isAll = (channelId == null || channelId == -1 && StringUtils.isEmpty(merchantCode));
        Page<Object> page = PageHelper.startPage(1, size);
        List<GlRecharge> glRecharges = exportRecharge(start, end, null, merchantCode, channelId,dto.getCoinCode());
        int pages = page.getPages();
        int total = Long.valueOf(page.getTotal()).intValue();
        //填装精装版数据
        List<GlRechargeExcelDto> resultList = new ArrayList<>(total);//拼装后的数据
        for (int i = 0; i < pages; i++) {
            if (i > 0) {
                PageHelper.startPage(i + 1, size, false);
                glRecharges = exportRecharge(start, end, null, dto.getMerchantCode(), dto.getChannelId(),dto.getCoinCode());
            }
            resultList.addAll(groupResult(getExportData(glRecharges), isAll));
        }
        log.info("——————————充值报表执行结束——————————");
        if (CollectionUtils.isEmpty(resultList)) {
            resultList = Lists.newArrayList(new GlRechargeExcelDto());
        }
        return resultList;
    }

    private List<RechargeExportResult> getExportData(List<GlRecharge> glRecharges) throws GlobalException {
        if (glRecharges == null || glRecharges.size() == 0) {
            return Lists.newArrayList();
        }
        List<GlRechargeDO> rechargeDOList = DtoUtils.transformList(glRecharges, GlRechargeDO.class);
        List<RechargeExportResult> resultList = new ArrayList<>(rechargeDOList.size());
        //订单号集合
        List<String> orderIds = rechargeDOList.stream().filter(r -> !StringUtils.isEmpty(r.getOrderId()))
                .map(GlRechargeDO::getOrderId).distinct().collect(Collectors.toList());
        List<Integer> userIds = rechargeDOList.stream().filter(r -> !StringUtils.isEmpty(r.getOrderId()))
                .map(GlRechargeDO::getUserId).distinct().collect(Collectors.toList());

        String joinOrderIds = String.format("'%s'", StringUtils.join(orderIds, "','"));
        List<GlRechargePay> pays = glRechargePayBusiness.findByIds(joinOrderIds);
        List<GlUserDO> users = RPCResponseUtils.getData(glUserService.findByIds(userIds));
        List<GlRechargeSuccessApprove> rsas = glRechargeSuccessApproveBusiness.findByIds(joinOrderIds);
        List<GlRechargeRelation> rechargeRelations = glRechargeRelationBusiness.findByIds(joinOrderIds);

        List<Integer> userLevelIds = rechargeDOList.stream().filter(r -> !StringUtils.isEmpty(r.getOrderId()))
                .map(GlRechargeDO::getUserLevel)
                .filter(StringUtils::isNotBlank)
                .map(Integer::parseInt)
                .distinct()
                .collect(Collectors.toList());
        List<GlFundUserlevel> userlevels = glFundUserlevelBusiness.findByLevelIds(userLevelIds);

        for (GlRechargeDO recharge : rechargeDOList) {
            if (ObjectUtils.isEmpty(recharge) || StringUtils.isEmpty(recharge.getOrderId())) {
                continue;
            }
            //充值支付记录存在，填充信息
            Optional<GlRechargePay> optionalPay = pays.stream()
                    .filter(p -> p.getOrderId().equals(recharge.getOrderId())).findFirst();
            optionalPay.ifPresent(p -> {
                recharge.setPayTime(p.getPayDate());
                recharge.setPayAmount(p.getAmount());
                recharge.setThirdOrderId(p.getThirdOrderId());
            });

            Optional<GlRechargeSuccessApprove> optionalApprove = rsas.stream()
                    .filter(a -> a.getOrderId().equals(recharge.getOrderId()))
                    .findFirst();
            optionalApprove.ifPresent(a -> {
                recharge.setSucApvAmount(a.getAmount());
                recharge.setSucApvOperator(a.getUsername());
                recharge.setSucApvTime(a.getCreateDate());
                recharge.setSucApvRemark(a.getRemark());
            });

            BigDecimal payAmount = BigDecimal.ZERO;
            if (recharge.getStatus() == 1) {
                // 补单审核成功
                if (recharge.getSubStatus() == 2) {
                    if (optionalApprove.isPresent()) {
                        payAmount = optionalApprove.get().getAmount();
                    }
                } else {
                    if (optionalPay.isPresent()) {
                        payAmount = optionalPay.get().getAmount();
                    }
                }
            }
            recharge.setPayAmount(payAmount);
            RechargeExportResult result = new RechargeExportResult();
            BeanUtils.copyProperties(recharge, result);

            users.stream().filter(u -> u.getId().equals(recharge.getUserId()))
                    .findFirst().ifPresent(u -> {
                result.setReallyName(u.getReallyName());
                result.setTelephone(u.getTelephone());
            });

            String mcode = "";
            if (!ObjectUtils.isEmpty(recharge.getMerchantCode())) {
                String[] codes = recharge.getMerchantCode().split("\\|\\|");
                mcode = codes[0];
            }
            result.setMerchantCode(mcode);
            //查询用户分层表转换用户层级
            userlevels.stream().filter(l -> result.getUserLevel().equals(l.getLevelId().toString()))
                    .findFirst().ifPresent(l -> result.setUserLevelName(l.getName()));
            //关联订单号
            try {
                result.setRelationOrderId("");
                rechargeRelations.stream().filter(r -> r.getOrderId().equals(recharge.getOrderId()))
                        .findFirst().ifPresent(r -> result.setRelationOrderId(r.getRelationOrderId()));
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            resultList.add(result);
        }

        // 产品确认备注类型为补单申请备注
        if (!CollectionUtils.isEmpty(orderIds)) {
            Map<String, String> remarkMap = glRechargeSuccessRequestBusiness.findRechargeRemark(orderIds);
            if (remarkMap != null) {
                for (RechargeExportResult item : resultList) {
                    item.setNewRemark(remarkMap.get(item.getOrderId()));
                }
            }
        }
        return resultList;
    }

    private List<GlRechargeExcelDto> groupResult(List<RechargeExportResult> resultList, boolean isAll) {
        // 先按照渠道分组
        Predicate<? super RechargeExportResult> criteriaByChannelId = recharge -> recharge.getChannelId() != null;
        Map<Integer, List<RechargeExportResult>> channelGroupMap = resultList.stream().filter(criteriaByChannelId).collect(Collectors.groupingBy(recharge -> recharge.getChannelId(), Collectors.toList()));
        List<RechargeExportResult> channelGropList;
        // 再按照商户号分组
        Map<String, List<RechargeExportResult>> merchantGroupMap;
        Predicate<? super RechargeExportResult> criteriaByMerchantCode = recharge -> recharge.getMerchantCode() != null;
        List<GlRechargeExcelDto> results = Lists.newLinkedList();
        if (isAll) { //导出全部报表
            results.addAll(convertRechargeData(0, null, resultList));
            return results;
        }
        for (Integer channelId : channelGroupMap.keySet()) {  //导出某一部分的数据
            channelGropList = channelGroupMap.get(channelId);
            merchantGroupMap = channelGropList.stream().filter(criteriaByMerchantCode).collect(Collectors.groupingBy(recharge -> recharge.getMerchantCode(), Collectors.toList()));
            for (String merchantCode : merchantGroupMap.keySet()) {
                results.addAll(convertRechargeData(channelId, merchantCode, merchantGroupMap.get(merchantCode)));
            }
        }
        return results;
    }
}
