package com.seektop.fund.business;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.seektop.common.encrypt.enums.builder.Encryptor;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.ConvertNameUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawReceiveInfoBusiness;
import com.seektop.fund.business.withdraw.WithdrawDownloadBusiness;
import com.seektop.fund.common.C2COrderDetailResult;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.dto.ExportFileDto;
import com.seektop.fund.controller.backend.dto.PageInfoExt;
import com.seektop.fund.controller.backend.result.FundUserLevelResult;
import com.seektop.fund.controller.backend.result.withdraw.GlWithdrawCollectResult;
import com.seektop.fund.handler.C2COrderCallbackHandler;
import com.seektop.fund.handler.C2COrderHandler;
import com.seektop.fund.handler.ExportFileHandler;
import com.seektop.fund.handler.WithdrawHandler;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.mapper.GlWithdrawReturnRequestMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.vo.*;
import com.seektop.system.service.GlExportService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.math.BigDecimal.*;


@Component
@Slf4j
public class GlWithdrawRecordBusiness {


    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;
    @Reference(retries = 2, timeout = 3000)
    private GlExportService glExportService;
    @Resource
    private RedisService redisService;
    @Resource
    private WithdrawDownloadBusiness withdrawDownloadBusiness;
    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;
    @Resource
    private GlWithdrawMapper glWithdrawMapper;
    @Resource
    private GlWithdrawReturnRequestMapper glWithdrawReturnreqMapper;
    @Autowired
    private ExportFileHandler exportFileHandler;
    @Autowired
    private GlWithdrawReceiveInfoBusiness infoBusiness;
    @Autowired
    private GlRechargeBusiness glRechargeBusiness;
    @Resource
    private C2COrderHandler c2COrderHandler;
    @Resource
    private C2COrderCallbackHandler c2COrderCallbackHandler;
    @Resource(name = "withdrawHandler")
    private WithdrawHandler withdrawHandler;


    /**
     * 提现出款列表
     */
    public PageInfoExt<GlWithdraw> getWithdrawPageList(WithdrawRecordListQueryDO queryDto) throws GlobalException {
        if ((queryDto.getStartTime() == null || queryDto.getEndTime() == null)
                && StringUtils.isEmpty(queryDto.getOrderId())
                && StringUtils.isEmpty(queryDto.getUserName())) {
            throw new GlobalException(ResultCode.DATA_ERROR, "必填参数丢失");
        }
        PageHelper.startPage(queryDto.getPage(), queryDto.getSize());
        List<GlWithdraw> record = new ArrayList<>();
        if (StringUtils.isNotEmpty(queryDto.getUserName())) {
            RPCResponse<GlUserDO> rpcGlUser = glUserService.getUserInfoByUsername(queryDto.getUserName());
            if (RPCResponseUtils.isFail(rpcGlUser)) {
                throw new GlobalException(ResultCode.SERVER_ERROR);
            }
            if (ObjectUtils.isEmpty(rpcGlUser.getData())) {
                return new PageInfoExt<>(record);
            }
            queryDto.setUserId(rpcGlUser.getData().getId());
        }
        record = glWithdrawMapper.getTransferRecord(queryDto);
        for (GlWithdraw glWithdraw : record) {
            GlFundUserlevel userLevel = glFundUserlevelBusiness.getUserLevel(glWithdraw.getUserId());
            if (userLevel != null) {
                glWithdraw.setUserLevel(userLevel.getName());
            }
        }
        /*
        按照币别分类单号,并且加总金额
        */
        queryDto.setPage(1);
        queryDto.setSize(99999);
        Map<String, Object> extInfo = new HashMap<>();
        extInfo.put("totalAmt",glWithdrawMapper.getTransferRecord(queryDto).stream().collect(
                Collectors.groupingBy(GlWithdraw::getCoin,
                        Collectors.mapping(GlWithdraw::getAmount, Collectors.summingDouble(BigDecimal::doubleValue))
                )
        ));
        extInfo.put("totalFee",glWithdrawMapper.getTransferRecord(queryDto).stream().collect(
                Collectors.groupingBy(GlWithdraw::getCoin,
                        Collectors.mapping(GlWithdraw::getFee, Collectors.summingDouble(BigDecimal::doubleValue))
                )
        ));
        PageInfoExt pageInfoExt = new PageInfoExt<>(record);
        pageInfoExt.setExtData(extInfo);
        return pageInfoExt;
    }


    /**
     * 提现出款列表汇总
     */
    public List<GlWithdrawAllCollect> getWithdrawPageListSum(WithdrawRecordListQueryDO queryDto) throws GlobalException {
        if ((queryDto.getStartTime() == null || queryDto.getEndTime() == null)
                && StringUtils.isEmpty(queryDto.getOrderId())
                && StringUtils.isEmpty(queryDto.getUserName())) {
            throw new GlobalException(ResultCode.DATA_ERROR, "必填参数丢失");
        }
        List<GlWithdrawAllCollect> list = new ArrayList<>();
        if (StringUtils.isNotEmpty(queryDto.getUserName())) {
            RPCResponse<GlUserDO> rpcGlUser = glUserService.getUserInfoByUsername(queryDto.getUserName());
            if (RPCResponseUtils.isFail(rpcGlUser)) {
                throw new GlobalException(ResultCode.SERVER_ERROR);
            }
            if (ObjectUtils.isEmpty(rpcGlUser.getData())) {
                return list;
            }
            queryDto.setUserId(rpcGlUser.getData().getId());
        }
        Map<String, DigitalCoinEnum> coinEnumMap = DigitalCoinEnum.getCoinMap().entrySet().stream()
                .filter(item -> item.getValue().getIsEnable())
                .filter(item -> (StringUtils.isEmpty(queryDto.getCoinCode())
                        || queryDto.getCoinCode().equals("-1")
                        || queryDto.getCoinCode().equals(item.getKey())))
                .collect(Collectors.toMap(p  -> p.getKey(),p -> p.getValue()));

        List<GlWithdrawAllCollect> record = glWithdrawMapper.getTransferRecordSum(queryDto);

        for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
            List<GlWithdrawAllCollect> tempList = record.stream().filter(item -> item.getCoinCode().equals(entry.getKey())).collect(Collectors.toList());
            GlWithdrawAllCollect glWithdrawAllCollect = new GlWithdrawAllCollect();
            glWithdrawAllCollect.setWithdrawAmountCollect(tempList.stream().map(item -> item.getWithdrawAmountCollect()).reduce(BigDecimal.ZERO,BigDecimal::add));
            glWithdrawAllCollect.setHandlingFeeAmountCollect(tempList.stream().map(item -> item.getHandlingFeeAmountCollect()).reduce(BigDecimal.ZERO,BigDecimal::add));
            glWithdrawAllCollect.setCoinCode(entry.getKey());
            glWithdrawAllCollect.setCount(tempList.size());
            list.add(glWithdrawAllCollect);
        }
        return list;
    }

    public GlWithdrawCollectResult<WithdrawVO> getWithdrawHistoryPageList(GlWithdrawQueryDto queryDto) throws GlobalException {
        if ((queryDto.getStartTime() == null || queryDto.getEndTime() == null)
                && StringUtils.isEmpty(queryDto.getOrderId())
                && StringUtils.isEmpty(queryDto.getUserName())) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }
        GlWithdrawCollectResult<WithdrawVO> pageInfo = new GlWithdrawCollectResult<>();
        PageHelper.startPage(queryDto.getPage(), queryDto.getSize());

        queryDto.setSortType(queryDto.getSortType() == null ? -1 : queryDto.getSortType());//默认-1创建时间
        String field = "create_date";
        switch (queryDto.getSortType()) {
            case -1:
                field = "create_date";
                break;
            case 0:
                field = "amount";
                break;
            case 1:
                field = "fee";
                break;
            case 2:
                field = "approve_time";
                break;
        }
        String sortStr = "desc";
        if (StringUtils.isNotEmpty(queryDto.getSortStr())) {
            if ("ascend".equals(queryDto.getSortStr())) {
                sortStr = "asc";
            }
        }

        queryDto.setOrderByClause(field);
        queryDto.setSortStr(sortStr);

        boolean isEmpty = setQueryParams(queryDto);
        if (isEmpty) {
            pageInfo.setList(Collections.emptyList());
            return pageInfo;
        }
        List<WithdrawVO> withdrawByPage = glWithdrawMapper.getWithdrawByPage(queryDto);
        List<Integer> userIds = withdrawByPage.stream().map(GlWithdraw::getUserId).distinct().collect(Collectors.toList());

        List<GlUserDO> users = RPCResponseUtils.getData(glUserService.findByIds(userIds));
        FundUserLevelResult result = glFundUserlevelBusiness.findByUserIds(userIds);

        List<String> orderIds = withdrawByPage.stream().map(GlWithdraw::getOrderId).collect(Collectors.toList());
        List<GlWithdrawReceiveInfo> withdrawReceiveInfos =  infoBusiness.findByIds(String.format("'%s'", StringUtils.join(orderIds, "','")));
        Map<String, GlWithdrawReceiveInfo> map = new HashMap<>(withdrawReceiveInfos.size());
        if (withdrawReceiveInfos != null && !withdrawReceiveInfos.isEmpty()) {
            map = withdrawReceiveInfos.stream().collect(Collectors.toMap( a -> a.getOrderId() ,a -> a));
        }
        List<GlWithdrawReturnRequest> returnRequests =  glWithdrawReturnreqMapper.selectByIds(String.format("'%s'", StringUtils.join(orderIds, "','")));
        Map<String, GlWithdrawReturnRequest> returnMap = new HashMap<>(returnRequests.size());
        if (returnRequests != null && !returnRequests.isEmpty()) {
            returnMap = returnRequests.stream().collect(Collectors.toMap( a -> a.getOrderId() ,a -> a));
        }

        for (WithdrawVO w : withdrawByPage) {

            Optional<GlUserDO> firstOpt = users.stream().filter(u -> u.getId().equals(w.getUserId())).findFirst();
            if (firstOpt.isPresent()) {
                GlUserDO user = firstOpt.get();
                // 脱敏处理
                w.setReallyName(Encryptor.builderName().doEncrypt(user.getReallyName()));
                w.setTelephone(Encryptor.builderMobile().doEncrypt(user.getTelephone()));
            }
            // 脱敏处理
            w.setName(Encryptor.builderName().doEncrypt(w.getName()));
            w.setCardNo(Encryptor.builderBankCard().doEncrypt(w.getCardNo()));
            w.setTransferBankCardNo(Encryptor.builderBankCard().doEncrypt(w.getTransferBankCardNo()));
            w.setTransferName(Encryptor.builderName().doEncrypt(w.getTransferName()));
            //用户层级
            Optional<GlFundUserlevel> filter = glFundUserlevelBusiness.filter(result, w.getUserId());
            filter.ifPresent(l -> w.setUserLevel(l.getName()));

            GlWithdrawReceiveInfo info = map.get(w.getOrderId());
            if (info != null){
                w.setRate(info.getRate());
                w.setUsdtAmount(info.getUsdtAmount());
                w.setActualUsdtAmount(info.getActualUsdtAmount());
            }
            GlRecharge glRecharge = glRechargeBusiness.findById(w.getThirdOrderId());
            Optional.ofNullable(glRecharge).ifPresent(obj -> {
                w.setRechargeName(obj.getUsername());
            });

            GlWithdrawReturnRequest returnRequest = returnMap.get(w.getOrderId());
            if (returnRequest != null) {
                w.setAttachments(returnRequest.getAttachments());
                if (org.apache.commons.lang3.ObjectUtils.isNotEmpty(returnRequest.getWithdrawStatus())
                        && returnRequest.getWithdrawStatus() == FundConstant.WithdrawStatus.RETURN_PART
                        && returnRequest.getStatus() == FundConstant.WithdrawApprove.SUCCESS) {
                    w.setActualAmount(w.getAmount().subtract(returnRequest.getAmount()));
                    w.setFee(ZERO);
                }
            }
            C2COrderDetailResult detailResult = c2COrderHandler.getByWithdrawOrderId(w.getOrderId(), w.getThirdOrderId());
            Optional.ofNullable(detailResult).ifPresent(obj -> {
                w.setMatchedDate(obj.getMatchedDate());
                w.setPaymentDate(obj.getPaymentDate());
                w.setReceiveDate(obj.getReceiveDate());
            });
            if (w.getStatus() == FundConstant.WithdrawStatus.SUCCESS || w.getStatus() == FundConstant.WithdrawStatus.FORCE_SUCCESS) {
                w.setActualAmount(w.getAmount().subtract(w.getFee()));
                w.setReceiveDate(w.getLastUpdate());
            }
            // 处理数字货币协议
            if (DigitalCoinEnum.getCoinList().contains(w.getCoin())) {
                w.setProtocol(w.getCardNo());
            }
        }
        return new GlWithdrawCollectResult<>(withdrawByPage);
    }

    /**
     * 查询用户信息 拼接用户reallyName名
     *
     * @param queryDto
     * @return
     * @throws GlobalException
     */
    public PageInfoExt<GlWithdrawReturnRequest> getWithdrawApprovePageList(WithdrawApproveListDO queryDto) throws GlobalException {
        PageHelper.startPage(queryDto.getPage(), queryDto.getSize());
        List<GlWithdrawReturnRequest> glWithdrawReturnRequests = new ArrayList<>();
        if (StringUtils.isNotEmpty(queryDto.getUserName())) {
            RPCResponse<GlUserDO> rpcResponse = glUserService.getUserInfoByUsername(queryDto.getUserName());
            if (RPCResponseUtils.isFail(rpcResponse)) {
                return new PageInfoExt<>(glWithdrawReturnRequests);
            }
            queryDto.setUserId(rpcResponse.getData().getId());
        }
        glWithdrawReturnRequests = glWithdrawReturnreqMapper.findByPage(queryDto);
        for (GlWithdrawReturnRequest request : glWithdrawReturnRequests) {
            GlUserDO glUser = RPCResponseUtils.getData(glUserService.findById(request.getUserId()));
            if (null != glUser) {
                request.setReallyName(glUser.getReallyName());
            }
            GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(request.getUserId());
            if (userlevel != null) {
                request.setUserLevel(userlevel.getLevelId());
                request.setUserLevelName(userlevel.getName());
            }
        }
        /*
        按照币别分类单号,并且加总金额
        */
        queryDto.setPage(1);
        queryDto.setSize(99999);
        Map<String, Object> extInfo = new HashMap<>();
        extInfo.put("totalAmt",glWithdrawReturnreqMapper.findByPage(queryDto).stream().collect(
                Collectors.groupingBy(GlWithdrawReturnRequest::getCoin,
                        Collectors.mapping(GlWithdrawReturnRequest::getAmount, Collectors.summingDouble(BigDecimal::doubleValue))
                )
        ));
        PageInfoExt pageInfoExt = new PageInfoExt<>(glWithdrawReturnRequests);
        pageInfoExt.setExtData(extInfo);
        return pageInfoExt;
    }

    /**
     * 提现记录汇总
     */
    public List<GlWithdrawAllCollect> getWithdrawCollect(GlWithdrawQueryDto queryDto) throws GlobalException {
        if ((queryDto.getStartTime() == null || queryDto.getEndTime() == null)
                && StringUtils.isEmpty(queryDto.getOrderId())
                && StringUtils.isEmpty(queryDto.getUserName())) {
            throw new GlobalException(ResultCode.DATA_ERROR, "必填参数丢失");
        }
        List<GlWithdrawAllCollect> list = Lists.newArrayList();

//        GlWithdrawAllCollect allCollect, collect , returnPartCoolect;
        List<GlWithdrawAllCollect> withdrawList = Lists.newArrayList();
        List<GlWithdrawAllCollect> arrivalList = Lists.newArrayList();
        List<GlWithdrawAllCollect> returnList = Lists.newArrayList();
        List<GlWithdrawAllCollect> arrivalCollectList = Lists.newArrayList();
        List<GlWithdrawAllCollect> returnlCollectList = Lists.newArrayList();
        List<GlWithdrawAllCollect> withdrawCollectList = Lists.newArrayList();

        BigDecimal returnAmount = ZERO;
        //部分到帐出款金额
        BigDecimal partArrivalAmount = BigDecimal.ZERO;

        Map<String, DigitalCoinEnum> coinEnumMap = DigitalCoinEnum.getCoinMap().entrySet().stream()
                .filter(item -> item.getValue().getIsEnable())
                .filter(item -> (StringUtils.isEmpty(queryDto.getCoinCode())
                        || queryDto.getCoinCode().equals("-1")
                        || queryDto.getCoinCode().equals(item.getKey())))
                .collect(Collectors.toMap(p  -> p.getKey(),p -> p.getValue()));

        // 按查询条件查询提现金额，手续费
        boolean isEmpty = setQueryParams(queryDto);
        if (isEmpty) {
            for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                GlWithdrawAllCollect allCollect = new GlWithdrawAllCollect();
                allCollect.setWithdrawAmountCollect(ZERO);
                allCollect.setHandlingFeeAmountCollect(ZERO);
                allCollect.setCount(0);
                allCollect.setCoinCode(entry.getKey());
                withdrawList.add(allCollect);
            }
        } else {
            withdrawList = glWithdrawMapper.getWithdrawCollect(queryDto);
            for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                List<GlWithdrawAllCollect> tempList = withdrawList.stream().filter(item -> item.getCoinCode().equals(entry.getKey())).collect(Collectors.toList());
                GlWithdrawAllCollect glWithdrawAllCollect = new GlWithdrawAllCollect();
                glWithdrawAllCollect.setWithdrawAmountCollect(tempList.stream().map(item -> item.getWithdrawAmountCollect()).reduce(BigDecimal.ZERO,BigDecimal::add));
                glWithdrawAllCollect.setHandlingFeeAmountCollect(tempList.stream().map(item -> item.getHandlingFeeAmountCollect()).reduce(BigDecimal.ZERO,BigDecimal::add));
                glWithdrawAllCollect.setCoinCode(entry.getKey());
                glWithdrawAllCollect.setCount(tempList.size());
                withdrawCollectList.add(glWithdrawAllCollect);
            }
        }

        // 查询到账金额，笔数
        List<Integer> status = queryDto.getWithdrawStatus();
        if (CollectionUtils.isEmpty(status)) {
            status = Lists.newArrayList(FundConstant.WithdrawStatus.SUCCESS,
                    FundConstant.WithdrawStatus.FORCE_SUCCESS);
        } else {
            status = status.stream()
                    .filter(s -> s.equals(FundConstant.WithdrawStatus.SUCCESS)
                            || s.equals(FundConstant.WithdrawStatus.FORCE_SUCCESS))
                    .collect(Collectors.toList());
        }
        if (CollectionUtils.isEmpty(status) || isEmpty) {
            for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                GlWithdrawAllCollect allCollect = new GlWithdrawAllCollect();
                allCollect.setWithdrawAmountCollect(ZERO);
                allCollect.setHandlingFeeAmountCollect(ZERO);
                allCollect.setCount(0);
                allCollect.setCoinCode(entry.getKey());
                arrivalList.add(allCollect);
                returnList.add(allCollect);
            }
        } else {
            queryDto.setWithdrawStatus(status);
            arrivalList = glWithdrawMapper.getWithdrawCollect(queryDto);
            for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                List<GlWithdrawAllCollect> tempList = arrivalList.stream().filter(item -> item.getCoinCode().equals(entry.getKey())).collect(Collectors.toList());
                GlWithdrawAllCollect glWithdrawAllCollect = new GlWithdrawAllCollect();
                glWithdrawAllCollect.setWithdrawAmountCollect(tempList.stream().map(item -> item.getWithdrawAmountCollect()).reduce(BigDecimal.ZERO,BigDecimal::add));
                glWithdrawAllCollect.setHandlingFeeAmountCollect(tempList.stream().map(item -> item.getHandlingFeeAmountCollect()).reduce(BigDecimal.ZERO,BigDecimal::add));
                glWithdrawAllCollect.setCoinCode(entry.getKey());
                glWithdrawAllCollect.setCount(tempList.size());
                arrivalCollectList.add(glWithdrawAllCollect);
            }

            queryDto.setWithdrawStatus(Lists.newArrayList(FundConstant.WithdrawStatus.RETURN_PART));
            returnList = glWithdrawMapper.getWithdrawCollect(queryDto);
            for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                List<GlWithdrawAllCollect> tempList = returnlCollectList.stream().filter(item -> item.getCoinCode().equals(entry.getKey())).collect(Collectors.toList());
                GlWithdrawAllCollect glWithdrawAllCollect = new GlWithdrawAllCollect();
                glWithdrawAllCollect.setWithdrawAmountCollect(tempList.stream().map(item -> item.getWithdrawAmountCollect()).reduce(BigDecimal.ZERO,BigDecimal::add));
                glWithdrawAllCollect.setHandlingFeeAmountCollect(tempList.stream().map(item -> item.getHandlingFeeAmountCollect()).reduce(BigDecimal.ZERO,BigDecimal::add));
                glWithdrawAllCollect.setCoinCode(entry.getKey());
                glWithdrawAllCollect.setCount(tempList.size());
                returnlCollectList.add(glWithdrawAllCollect);
            }

            //提现部分退回
            Map<String, Object> param = new HashMap<>();
            param.put("userId", queryDto.getUserId());
            param.put("userIds", queryDto.getUserIdList());
            param.put("startDate", queryDto.getStartTime());
            param.put("endDate", queryDto.getEndTime());
            param.put("withdrawStatus", FundConstant.WithdrawStatus.RETURN_PART);
            param.put("status" , 1);
            param.put("orderId" , queryDto.getOrderId());
            returnAmount = glWithdrawReturnreqMapper.getAmountTotal(param);

            for (GlWithdrawAllCollect returnCollect:returnlCollectList) {
                if (returnCollect.getCoinCode().equals(DigitalCoinEnum.CNY.getCode())){
                    partArrivalAmount = returnCollect.getWithdrawAmountCollect().subtract(returnAmount);
                }
            }
        }

        List<GlWithdrawAllCollect> finalWithdrawList = withdrawCollectList;
        List<GlWithdrawAllCollect> finalReturnList = returnlCollectList;
        BigDecimal finalPartArrivalAmount = partArrivalAmount;
        arrivalCollectList.stream().forEach(item -> {
            item.setArrivalAmountCollect(item.getWithdrawAmountCollect().subtract(item.getHandlingFeeAmountCollect()));
            if (item.getCoinCode().equals(DigitalCoinEnum.CNY.getCode())) {
                item.setArrivalAmountCollect(item.getArrivalAmountCollect().add(finalPartArrivalAmount));
            }
            finalWithdrawList.stream().filter(obj -> obj.getCoinCode().equals(item.getCoinCode())).findFirst().ifPresent(obj ->{
                item.setWithdrawAmountCollect(obj.getWithdrawAmountCollect());
                finalReturnList.stream().filter(r -> r.getCoinCode().equals(item.getCoinCode())).findFirst().ifPresent(r2 -> {
                    item.setHandlingFeeAmountCollect(obj.getHandlingFeeAmountCollect().subtract(r2.getHandlingFeeAmountCollect()));
                });
            });
            list.add(item);
        });
        return list;
    }

    private boolean setQueryParams(GlWithdrawQueryDto queryDto) throws GlobalException {
        //过滤用户
        List<Integer> idList = Optional.ofNullable(queryDto.getUserIdList()).orElse(Lists.newArrayList());
        //根据姓名查询
        if (StringUtils.isNotEmpty(queryDto.getReallyName())) {
            RPCResponse<List<GlUserDO>> rpcUser = glUserService.findByReallyName(queryDto.getReallyName());
            List<GlUserDO> glUser = RPCResponseUtils.getData(rpcUser);
            if (null == glUser || glUser.size() == 0) {
                return true;
            }
            if (glUser.size() > 1) {
                idList.addAll(glUser.stream().map(GlUserDO::getId).collect(Collectors.toList()));
            } else {
                queryDto.setUserId(glUser.get(0).getId());
            }
        }
        //根据手机号查询用户
        if (StringUtils.isNotEmpty(queryDto.getTelephone())) {
            RPCResponse<List<GlUserDO>> rpcUser = glUserService.findByTelephone(queryDto.getTelephone());
            List<GlUserDO> glUser = RPCResponseUtils.getData(rpcUser);
            if (null == glUser || glUser.size() == 0) {
                return true;
            }

            if (glUser.size() > 1) {
                idList.addAll(glUser.stream().map(GlUserDO::getId).collect(Collectors.toList()));
            } else {
                queryDto.setUserId(glUser.get(0).getId());
            }
        }
        //根据用户名查询
        if (StringUtils.isNotEmpty(queryDto.getUserName())) {
            RPCResponse<GlUserDO> rpcResponse = glUserService.getUserInfoByUsername(queryDto.getUserName());
            if (RPCResponseUtils.isFail(rpcResponse)) {
                return true;
            }
            queryDto.setUserId(rpcResponse.getData().getId());
        }
        if (idList.size() > 0) {
            queryDto.setUserIdList(idList);
        }
        return false;
    }

    public void export(GlWithdrawQueryDto queryDto, GlAdminDO adminDO) throws GlobalException {
        String lockKey = "WITHDRAW_DOWNLOAD_LOCK_" + adminDO.getUserId();
        String lockValue = redisService.get(lockKey);
        if ("1".equals(lockValue)) {
            throw new GlobalException(ResultCode.ILLEGAL_REQUEST, "5分钟只能导出一次");
        }
        try {
            withdrawDownloadBusiness.downLoadList(queryDto, adminDO.getUserId());//异步导出数据
        } catch (Exception e) {
            log.error("withdrawDownloadBusiness.downLoadList error", e);
        }
        redisService.set(lockKey, "1", 300);
    }

    public GlWithdrawAllCollect getMemberWithdrawTotal(GlWithdrawQueryDto queryDto) {
        GlWithdrawAllCollect glWithdrawAllCollect = new GlWithdrawAllCollect();
        try {
            glWithdrawAllCollect = glWithdrawMapper.memberWithdrawTotal(queryDto);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return glWithdrawAllCollect;
    }

    @Async
    public void withdrawListExport(GlWithdrawQueryDto queryDto, Integer adminId) {
        ExportFileDto exportFileDto = new ExportFileDto();
        exportFileDto.setUserId(adminId);
        exportFileDto.setFileName("提现记录");
        exportFileDto.setHeaders("提现时间,提现单号,提现金额,手续费,出款金额,银行名称,银行卡号,操作端,出款时间,订单状态,备注");
        exportFileDto.setSupplier(() -> getExportData(queryDto));
        exportFileHandler.exportFile(exportFileDto);
    }

    private StringBuffer getExportData(GlWithdrawQueryDto queryDto) throws GlobalException {
        int size = 2_000;
        queryDto.setPage(1);
        queryDto.setSize(size);
        GlWithdrawCollectResult<WithdrawVO> page = getWithdrawHistoryPageList(queryDto);
        int pages = Math.min(page.getPages(), 100);//限制上限20W;
        List<WithdrawVO> records = page.getList();
        StringBuffer data = new StringBuffer();
        for (int i = 0; i < pages; i++) {
            if (i > 0) {
                queryDto.setPage(i + 1);
                page = getWithdrawHistoryPageList(queryDto);
                records = page.getList();
            }
            for (WithdrawVO record : records) {
                data.append(getExportData(record)).append("\r\n");
            }
        }
        return data;
    }

    private StringBuffer getExportData(WithdrawVO item) {
        StringBuffer sb = new StringBuffer();
        //提现时间
        sb.append(DateUtils.format(item.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS)).append(",")
                //提现单号
                .append(item.getOrderId()).append(",")
                //提现金额
                .append(BigDecimalUtils.ifNullSet0(item.getAmount())).append(",")
                // 手续费
                .append(BigDecimalUtils.ifNullSet0(item.getFee())).append(",")
                //出款金额
                .append(BigDecimalUtils.ifNullSet0(this.getConfirmAmount(item))).append(",")
                //银行名称
                .append(item.getBankName()).append(",")
                //  银行卡号
                .append(item.getCardNo()).append(",")
                //  操作端
                .append(ObjectUtils.isEmpty(item.getClientType()) ? "" :
                        ConvertNameUtils.convertOsTypeName(item.getClientType()))
                .append(",")
                // 出款时间
                .append(DateUtils.format(item.getLastUpdate(), DateUtils.YYYY_MM_DD_HH_MM_SS)).append(",")
                //订单状态
                .append(getStatusName(item.getStatus())).append(",")
                //备注
                .append("\"" + Optional.ofNullable(item.getRemark()).orElse("") + "\"").append(",");
        return sb;
    }

    public BigDecimal getConfirmAmount(WithdrawVO item){
        BigDecimal decimal = ZERO;
        Integer status = item.getStatus();
        BigDecimal amount = item.getAmount();
        BigDecimal fee = item.getFee();
        if(status == FundConstant.WithdrawStatus.SUCCESS || status == FundConstant.WithdrawStatus.FORCE_SUCCESS
                || status == FundConstant.WithdrawStatus.RETURN_PART){
            decimal = amount.subtract(fee);//出款確認金額=提現金額-手續費
            if (status == FundConstant.WithdrawStatus.RETURN_PART) {
               GlWithdrawReturnRequest returnRequest =  glWithdrawReturnreqMapper.selectByPrimaryKey(item.getOrderId());
               decimal = amount.subtract(returnRequest.getAmount());//收款金额 = 提现金额 - 退回金额
            }
        }
        return decimal;
    }

    public String getStatusName(Integer status){
        if (ObjectUtils.isEmpty(status))
            return "";
        switch (status){
            case -4:
                return "搁置";
            case -3:
                return "风险待审核";
            case -2:
                return "风险审核拒绝";
            case 0:
                return "风险审核通过(待出款)";
            case 1:
                return "出款成功";
            case 2:
                return "出款失败";
            case 3:
                return "拒绝出款(退回)";
            case 4:
                return "已退回";
            case 5:
                return "拒绝退回";
            case 6:
                return "申请强制成功中";
            case 7:
                return "自动出款失败";
            case 8:
                return "已经强制成功";
            case 9:
                return "拒绝强制成功";
            case 10:
                return "三方自动出款中";
            case 11:
                return "出款专员处理中";
            case 12:
                return "超时未确认到账";
            case 13:
                return "拒绝出款（部分退回）";
            case 14:
                return "已退回部分";
            case 15:
                return "待确认到帐";
            case 16:
                return "待付款";
        }
        return "";
    }

    public int getWithdrawApproveTips() {
        Condition e = new Condition(GlWithdrawReturnRequest.class);
        e.createCriteria().andEqualTo("status",0);
        return glWithdrawReturnreqMapper.selectCountByCondition(e);
    }
}
