package com.seektop.fund.handler.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.seektop.common.encrypt.enums.builder.Encryptor;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ExportConfigEnum;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentBusiness;
import com.seektop.fund.business.recharge.*;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.dto.ExportFileDto;
import com.seektop.fund.controller.backend.dto.PageInfoExt;
import com.seektop.fund.controller.backend.result.FundUserLevelResult;
import com.seektop.fund.controller.backend.result.recharge.GlRechargeCollectResult;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.handler.ExportFileHandler;
import com.seektop.fund.handler.RechargeRecordHandler;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.mapper.GlRechargeSuccessApproveMapper;
import com.seektop.fund.mapper.GlRechargeSuccessRequestMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.vo.*;
import com.seektop.system.service.GlExportService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Component("rechargeRecordHandler")
public class RechargeRecordHandlerImpl implements RechargeRecordHandler {

    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;
    @Reference(retries = 2, timeout = 3000)
    private GlExportService glExportService;
    @Resource
    private RedisService redisService;
    @Resource
    private RechargeExportBusiness rechargeExportBusiness;
    @Resource
    private GlRechargeRelationBusiness glRechargeRelationBusiness;
    @Resource
    private GlRechargePayBusiness glRechargePayBusiness;
    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;
    @Resource
    private GlRechargeMapper glRechargeMapper;
    @Resource
    private GlRechargeSuccessApproveMapper glRechargeSuccessApproveMapper;
    @Resource
    private GlRechargeSuccessApproveBusiness glRechargeSuccessApproveBusiness;
    @Resource
    private GlRechargeSuccessRequestMapper glRechargeSuccessRequestMapper;
    @Autowired
    private ExecutorService executorService;
    @Resource
    private GlRechargeBusiness glRechargeBusiness;
    @Autowired
    private ExportFileHandler exportFileHandler;
    @Autowired
    private GlPaymentBusiness paymentBusiness;
    @Resource
    private GlRechargeReceiveInfoBusiness glRechargeReceiveInfoBusiness;
    @Resource
    private RechargeRecordHandler rechargeRecordHandler;
    @Resource
    private DynamicKey dynamicKey;

    public GlRechargeCollectResult<GlRechargeDO> findRechargeRecordPageList(RechargeQueryDto queryDto) throws GlobalException {
        long start = System.currentTimeMillis();
        log.info("[充值记录查询]进入充值记录查询列表,当前时间是{}", start);
        if ((queryDto.getStartTime() == null || queryDto.getEndTime() == null)
                && StringUtils.isEmpty(queryDto.getOrderId())
                && StringUtils.isEmpty(queryDto.getUserName())) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }
        if (null != queryDto.getAgentType() && queryDto.getAgentType() == -1) {
            queryDto.setAgentType(null);
        }

        // 添加排序
        queryDto.setOrderByClause(this.getOrderByColumn(queryDto.getSortType()));
        queryDto.setSortStr(this.getSortStr(queryDto.getSortOrder()));

        Page<GlRechargeDO> page = PageHelper.startPage(queryDto.getPage(), queryDto.getSize());

        boolean isEmpty = setQueryParams(queryDto);
        if (isEmpty) {
            return new GlRechargeCollectResult<>(Lists.newArrayList());
        }
        long startQuery = System.currentTimeMillis();
        log.info("[充值记录查询]准备开始查询数据,当前时间是{}", startQuery);
        List<GlRecharge> records = glRechargeMapper.findRechargePageList(queryDto);
        long endQuery = System.currentTimeMillis();
        log.info("[充值记录查询]数据查询成功,当前时间是{},累计耗时{}", endQuery, endQuery - startQuery);
        if (CollectionUtils.isEmpty(records)) {
            return new GlRechargeCollectResult<>(Lists.newArrayList());
        }
        //查询该单号相关的补单信息
        if (StringUtils.isNotBlank(queryDto.getOrderId())) {
            List<GlRechargeRelation> relations = glRechargeRelationBusiness.findAllBy("relationOrderId", queryDto.getOrderId());
            if (!relations.isEmpty()) {
                List<String> idList = relations.stream().map(GlRechargeRelation::getOrderId).collect(Collectors.toList());
                String ids = "'" + StringUtils.join(idList, "','") + "'";
                List<GlRecharge> relationLists = glRechargeMapper.selectByIds(ids);
                records.addAll(relationLists);
            }
        }

        // 异步查询数据
        CompletableFuture<?>[] futures = getDataByAsync(records);
        List<GlUserDO> users = RPCResponseUtils.getData((RPCResponse<List<GlUserDO>>) futures[0].join());
        FundUserLevelResult levelResult = (FundUserLevelResult) futures[1].join();
        List<GlRechargeRelation> relations = (List<GlRechargeRelation>) futures[2].join();
        List<GlRechargePay> pays = (List<GlRechargePay>) futures[3].join();
        List<GlRechargeSuccessApprove> approves = (List<GlRechargeSuccessApprove>) futures[4].join();
        List<GlRechargeReceiveInfo> receiveInfos = (List<GlRechargeReceiveInfo>) futures[5].join();

        List<GlRechargeDO> list = records.stream().map(record -> {
            GlRechargeDO recharge = new GlRechargeDO();
            BeanUtils.copyProperties(record, recharge);

            //关联订单号
            if (!CollectionUtils.isEmpty(relations)) {
                Optional<GlRechargeRelation> first = relations.stream()
                        .filter(r -> r.getOrderId().equals(recharge.getOrderId())).findFirst();
                first.ifPresent(glRechargeRelation -> recharge.setOriginalOrderId(glRechargeRelation.getRelationOrderId()));
            }
            if (!CollectionUtils.isEmpty(pays)) {
                Optional<GlRechargePay> first = pays.stream().filter(r -> r.getOrderId().equals(recharge.getOrderId())).findFirst();
                if (first.isPresent()) {
                    GlRechargePay pay = first.get();
                    recharge.setPayTime(pay.getPayDate());
                    recharge.setPayAmount(pay.getAmount());
                    recharge.setThirdOrderId(pay.getThirdOrderId());
                }
            }
            if (recharge.getStatus() == ProjectConstant.RechargeStatus.SUCCESS
                    && recharge.getSubStatus() != null
                    && recharge.getSubStatus() == 2) {
                // 补单审核成功
                if (!CollectionUtils.isEmpty(approves)) {
                    Optional<GlRechargeSuccessApprove> first = approves.stream()
                            .filter(r -> r.getOrderId().equals(recharge.getOrderId()))
                            .findFirst();
                    if (first.isPresent()) {
                        GlRechargeSuccessApprove approve = first.get();
                        recharge.setSucApvTime(approve.getCreateDate()); //补单审核时间
                        recharge.setRemark(approve.getRemark());
                    }
                }
            }

            if (!CollectionUtils.isEmpty(users)) {
                Optional<GlUserDO> first = users.stream().filter(u -> u.getId().equals(recharge.getUserId())).findFirst();
                if (first.isPresent()) {
                    GlUserDO user = first.get();
                    recharge.setReallyName(Encryptor.builderName().doEncrypt(user.getReallyName()));
                    recharge.setTelephone(Encryptor.builderMobile().doEncrypt(user.getTelephone()));
                }
            }

            if (!CollectionUtils.isEmpty(receiveInfos)) {
                Optional<GlRechargeReceiveInfo> first = receiveInfos.stream()
                        .filter(r -> r.getOrderId().equals(recharge.getOrderId()))
                        .findFirst();
                if (first.isPresent()) {
                    GlRechargeReceiveInfo receiveInfo = first.get();
                    recharge.setUsdtAmount(receiveInfo.getDigitalAmount());
                    recharge.setRate(receiveInfo.getRate());
                    recharge.setPayRate(receiveInfo.getRealRate());
                    recharge.setBlockAddress(receiveInfo.getBlockAddress());
                    recharge.setPayRate(receiveInfo.getRealRate());
                    recharge.setProtocol(receiveInfo.getProtocol());
                    recharge.setTxHash(receiveInfo.getTxHash());
                    if ((recharge.getPaymentId() == FundConstant.PaymentType.DIGITAL_PAY || recharge.getPaymentId() == FundConstant.PaymentType.RMB_PAY )
                            && StringUtils.isNotEmpty(receiveInfo.getKeyword())) {
                        try {
                            recharge.setUsdtPayAmount(new BigDecimal(receiveInfo.getKeyword()));
                        } catch (Exception e) {
                            log.error("转换实际USDT数量异常",receiveInfo.getKeyword());
                        }
                    }
                    if (!ObjectUtils.isEmpty(recharge.getChannelId()) && recharge.getChannelId() == FundConstant.PaymentChannel.C2CPay) {
                        recharge.setMerchantCode(receiveInfo.getKeyword());
                        recharge.setThirdOrderId(receiveInfo.getThirdOrderId());
                    }
                }
            }
            // 银行卡号脱敏
            recharge.setCardNo(Encryptor.builderBankCard().doEncrypt(recharge.getCardNo()));
            Optional<GlFundUserlevel> filter = glFundUserlevelBusiness.filter(levelResult, recharge.getUserId());
            filter.ifPresent(obj -> recharge.setUserLevel(obj.getName()));

            recharge.setPaymentName(FundLanguageUtils.getPaymentName(recharge.getPaymentId(), recharge.getPaymentName(), queryDto.getLanguage()));
            return recharge;
        }).collect(Collectors.toList());
        GlRechargeCollectResult<GlRechargeDO> pageInfo = new GlRechargeCollectResult<>(page);
        pageInfo.setList(list);

        Map<String, DigitalCoinEnum> coinEnumMap = DigitalCoinEnum.getCoinMap().entrySet().stream()
                .filter(item -> item.getValue().getIsEnable()).collect(Collectors.toMap(p  -> p.getKey(),p -> p.getValue()));


        if (queryDto.getIncludeTotal()) {
            List<GlRechargeAllCollect> allCollects = Lists.newArrayList();
            List<GlRechargeAllCollect> rechargeAllCollects = Lists.newArrayList();

            List<GlRechargeCollect> glRechargeCollects = glRechargeBusiness.getMemberRechargeTotal(queryDto);


            List<Integer> status = queryDto.getOrderStatus();
            // 查询成功的到账金额，笔数
            if (isEmpty || CollectionUtils.isEmpty(status) || status.stream().noneMatch(s -> s.equals(1) || s.equals(2))) {
                for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                    List<GlRechargeCollect> tempList = glRechargeCollects.stream().filter(item -> item.getCoinCode().equals(entry.getKey())).collect(Collectors.toList());
                    GlRechargeAllCollect glRechargeAllCollect = new GlRechargeAllCollect();
                    glRechargeAllCollect.setDepositAmountCollect(tempList.stream().map(item -> item.getDepositAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                    glRechargeAllCollect.setHandlingFeeAmountCollect(tempList.stream().map(item -> item.getHandlingFeeAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                    glRechargeAllCollect.setCoinCode(entry.getKey());
                    glRechargeAllCollect.setArrivalAmountCollect(BigDecimal.ZERO);
                    glRechargeAllCollect.setCount(0);
                    rechargeAllCollects.add(glRechargeAllCollect);
                }
            } else {
                queryDto.setOrderStatus(status.stream().filter(s -> s.equals(1) || s.equals(2)).collect(Collectors.toList()));
                List<GlRechargeCollect> successRechargeCollects = glRechargeBusiness.getMemberRechargeTotal(queryDto);
                log.info("successRechargeCollects:{}",JSON.toJSONString(successRechargeCollects));
                List<GlRechargeAllCollect> arrivalAllCollects = Lists.newArrayList();
                for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                    List<GlRechargeCollect> tempList = successRechargeCollects.stream().filter(item -> item.getCoinCode().equals(entry.getKey())).collect(Collectors.toList());
                    GlRechargeAllCollect glRechargeAllCollect = new GlRechargeAllCollect();
                    glRechargeAllCollect.setDepositAmountCollect(tempList.stream().map(item -> item.getDepositAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                    glRechargeAllCollect.setHandlingFeeAmountCollect(tempList.stream().map(item -> item.getHandlingFeeAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                    glRechargeAllCollect.setArrivalAmountCollect(tempList.stream().map(item -> item.getArrivalAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                    glRechargeAllCollect.setCoinCode(entry.getKey());
                    glRechargeAllCollect.setCount(tempList.size());
                    arrivalAllCollects.add(glRechargeAllCollect);
                }
                log.info("arrivalAllCollects:{}",JSON.toJSONString(arrivalAllCollects));


                if (!CollectionUtils.isEmpty(arrivalAllCollects)) {
                    List<GlRechargeAllCollect> finalRechargeDatas = rechargeAllCollects;
                    arrivalAllCollects.stream().forEach(item -> {
                        item.setArrivalAmountCollect(item.getArrivalAmountCollect().subtract(item.getHandlingFeeAmountCollect()));
                        finalRechargeDatas.stream().filter(recharge -> recharge.getCoinCode().equals(item.getCoinCode()))
                                .findFirst().ifPresent(obj -> item.setDepositAmountCollect(obj.getDepositAmountCollect()));
                        allCollects.add(item);
                    });
                }
                log.info("allCollects:{}",JSON.toJSONString(allCollects));

            }
            pageInfo.setGlRechargeAllCollect(allCollects);
        }
        return pageInfo;
    }

    /**
     * 设置相应查询参数
     *
     * @param queryDto
     * @return
     * @throws GlobalException
     */
    private boolean setQueryParams(RechargeQueryDto queryDto) throws GlobalException {
        List<Integer> userIdList = Optional.ofNullable(queryDto.getUserIdList()).orElse(Lists.newArrayList());
        /**
         * 优先级： 用户名>真实姓名>手机号
         */
        //根据手机号查询用户
        if (StringUtils.isNotEmpty(queryDto.getTelephone())) {
            RPCResponse<List<GlUserDO>> rpcUser = glUserService.findByTelephone(queryDto.getTelephone());
            List<GlUserDO> glUser = RPCResponseUtils.getData(rpcUser);
            if (null == glUser || glUser.size() == 0) {
                return true;
            }
            if (glUser.size() > 1) {
                userIdList.addAll(glUser.stream().map(GlUserDO::getId).collect(Collectors.toList()));
            } else {
                queryDto.setUserId(glUser.get(0).getId());
            }
        }
        //根据姓名查询
        if (StringUtils.isNotEmpty(queryDto.getReallyName())) {
            RPCResponse<List<GlUserDO>> rpcUser = glUserService.findByReallyName(queryDto.getReallyName());
            List<GlUserDO> glUser = RPCResponseUtils.getData(rpcUser);
            if (null == glUser || glUser.size() == 0) {
                return true;
            }
            if (glUser.size() > 1) {
                userIdList.addAll(glUser.stream().map(GlUserDO::getId).collect(Collectors.toList()));
            } else {
                queryDto.setUserId(glUser.get(0).getId());
            }
        }

        queryDto.setUserIdList(userIdList);

        //根据用户名搜索
        if (StringUtils.isNotEmpty(queryDto.getUserName())) {
            RPCResponse<GlUserDO> rpcResponse = glUserService.getUserInfoByUsername(queryDto.getUserName());
            if (RPCResponseUtils.isFail(rpcResponse)) {
                return true;
            }
            queryDto.setUserId(rpcResponse.getData().getId());
        }
        // 到账时间查询时，不包括充值成功和补单成功状态，返回空列表
        if (null != queryDto.getDateType() && 1 != queryDto.getDateType()) {
            List<Integer> status = queryDto.getOrderStatus();
            if (CollectionUtils.isEmpty(status) || status.stream().noneMatch(s -> s.equals(1) || s.equals(2))) {
                return true;
            }
        }
        //根据订单状态搜索
        this.setStatus(queryDto);

        //Size = 14 查询全部充值方式
        if (null != queryDto.getPaymentIdList()
                && queryDto.getPaymentIdList().size() == 20) {
            queryDto.setPaymentIdList(null);
        }

        //Size = 4 查询全部应用端充值数据
        if (null != queryDto.getClientTypeList() &&
                queryDto.getClientTypeList().size() == 4) {
            queryDto.setClientTypeList(null);
        }
        return false;
    }

    /**
     * @param sortType -1.充值时间(创建时间) 0.充值金额 1.手续费 2.到账时间
     * @return
     * @see
     */
    private String getOrderByColumn(Integer sortType) {
        String order = "create_date";
        if (ObjectUtils.isEmpty(sortType)) return order;
        switch (sortType) {
            case -1:
                order = "create_date";
                break;
            case 0:
                order = "amount";
                break;
            case 1:
                order = "fee";
                break;
            default:
                break;
        }
        return order;
    }

    /**
     * 排序方式
     *
     * @param sortOrder
     * @return
     */
    private static String getSortStr(Integer sortOrder) {
        String sortStr = "desc";
        if (!ObjectUtils.isEmpty(sortOrder) && sortOrder <= 0) {
            sortStr = "asc";
        }
        return sortStr;
    }

    private void setStatus(RechargeQueryDto queryDto) {
        if (null != queryDto.getOrderStatus()
                && queryDto.getOrderStatus().size() != 0
                && queryDto.getOrderStatus().size() != 10) {
            if (queryDto.getDateType() == 1 && !queryDto.getMainStatus()) {//根据充值时间查询
                Set<Integer> statusS = new HashSet<>();
                Set<Integer> subStatusS = new HashSet<>();
                queryDto.getOrderStatus().stream().forEach(item -> {
                    switch (item) {
                        case 0: // 待支付
                            statusS.add(ProjectConstant.RechargeStatus.PENDING);
                            break;
                        case 1: // 支付成功
                            statusS.add(ProjectConstant.RechargeStatus.SUCCESS);
                            subStatusS.add(1);
                            break;
                        case 2: // 补单审核成功
                            statusS.add(ProjectConstant.RechargeStatus.SUCCESS);
                            subStatusS.add(2);
                            break;
                        case 3: // 补单审核拒绝
                            statusS.add(ProjectConstant.RechargeStatus.FAILED);
                            subStatusS.add(3);
                            break;
                        case 4: //人工拒绝补单
                            statusS.add(ProjectConstant.RechargeStatus.FAILED);
                            subStatusS.add(4);
                            break;
                        case 5: //补单审核中
                            statusS.add(ProjectConstant.RechargeStatus.REVIEW);
                            break;
                        case 6: //用户撤销
                            statusS.add(ProjectConstant.RechargeStatus.FAILED);
                            subStatusS.add(5);
                            break;
                        case 7: //超时撤销
                            statusS.add(ProjectConstant.RechargeStatus.FAILED);
                            subStatusS.add(6);
                            break;
                        case 8: //待确认到帐
                            statusS.add(ProjectConstant.RechargeStatus.PENDING);
                            subStatusS.add(7);
                            break;
                        case 9: //超时未确认到帐
                            statusS.add(ProjectConstant.RechargeStatus.PENDING);
                            subStatusS.add(8);
                            break;
                    }
                });
                List<Integer> status = statusS.stream().collect(Collectors.toList());
                List<Integer> subStatus = subStatusS.stream().collect(Collectors.toList());
                // 单独过滤支付成功
                if (status.size() == 1 && status.get(0) == ProjectConstant.RechargeStatus.SUCCESS
                        && subStatus.size() == 1 && subStatus.get(0) == 1) {
                    queryDto.setSubStatus(subStatus);
                } else {
                    queryDto.setStatus(status);
                    queryDto.setSubStatus(subStatus);
                }
            } else if (queryDto.getDateType() == 1 && queryDto.getMainStatus()) {
                List<Integer> status = queryDto.getOrderStatus().stream().collect(Collectors.toList());
                queryDto.setStatus(status);
                queryDto.setSubStatus(null);
            }else if (queryDto.getDateType() == 2) { //根据到账时间查询
                queryDto.setSuccStatus(-1);
                if (queryDto.getOrderStatus().contains(ProjectConstant.RechargeStatus.SUCCESS) &&
                        !queryDto.getOrderStatus().contains(2)) { //单独查询支付成功
                    queryDto.setSuccStatus(1);
                } else if (!queryDto.getOrderStatus().contains(ProjectConstant.RechargeStatus.SUCCESS) &&
                        queryDto.getOrderStatus().contains(2)) {//单独查询补单审核成功
                    queryDto.setSuccStatus(2);
                }
            }
        }
    }

    /**
     * 异步查询数据
     *
     * @param list
     * @return
     */
    private CompletableFuture<?>[] getDataByAsync(List<GlRecharge> list) {
        //会员信息查询
        CompletableFuture<List<Integer>> future = CompletableFuture
                .supplyAsync(() -> list.stream().map(GlRecharge::getUserId).distinct().collect(Collectors.toList()), executorService);
        CompletableFuture<RPCResponse<List<GlUserDO>>> f0 = future
                .thenApplyAsync(userIds -> glUserService.findByIds(userIds), executorService);
        CompletableFuture<FundUserLevelResult> f1 = future
                .thenApplyAsync(userIds -> glFundUserlevelBusiness.findByUserIds(userIds), executorService);


        CompletableFuture<String> future1 = CompletableFuture
                .supplyAsync(() -> {
                    List<String> orderIds = list.stream().map(GlRecharge::getOrderId).distinct().collect(Collectors.toList());
                    return "'" + StringUtils.join(orderIds, "','") + "'";
                }, executorService);
        //关联订单信息查询
        CompletableFuture<List<GlRechargeRelation>> f2 = future1.
                thenApplyAsync(orderIds -> glRechargeRelationBusiness.findByIds(orderIds), executorService);
        //订单支付GlRechargePay信息查询
        CompletableFuture<List<GlRechargePay>> f3 = future1.
                thenApplyAsync(orderIds -> glRechargePayBusiness.findByIds(orderIds), executorService);
        //充值订单审核信息查询
        CompletableFuture<List<GlRechargeSuccessApprove>> f4 = CompletableFuture
                .supplyAsync(() -> list.stream()
                        .filter(r -> ProjectConstant.RechargeStatus.SUCCESS == r.getStatus())
                        .filter(r -> r.getSubStatus() != null && r.getSubStatus() == 2)
                        .map(GlRecharge::getOrderId).distinct().collect(Collectors.toList()), executorService)
                .thenApplyAsync(orderIds -> {
                    if (CollectionUtils.isEmpty(orderIds)) {
                        return null;
                    }
                    String ids = "'" + StringUtils.join(orderIds, "','") + "'";
                    return glRechargeSuccessApproveBusiness.findByIds(ids);
                }, executorService);

        //订单支付详情
        CompletableFuture<List<GlRechargeReceiveInfo>> f5 = CompletableFuture
                .supplyAsync(() -> list.stream()
                        .filter(r -> (r.getPaymentId().equals(FundConstant.PaymentType.DIGITAL_PAY)
                                || r.getPaymentId().equals(FundConstant.PaymentType.RMB_PAY)
                                || r.getChannelId().equals(FundConstant.PaymentChannel.C2CPay)))
                        .map(GlRecharge::getOrderId).distinct().collect(Collectors.toList()), executorService)
                .thenApplyAsync(orderIds -> {
                    if (CollectionUtils.isEmpty(orderIds)) {
                        return null;
                    }
                    String ids = "'" + StringUtils.join(orderIds, "','") + "'";
                    return glRechargeReceiveInfoBusiness.findByIds(ids);
                }, executorService);


        CompletableFuture<?>[] futures = new CompletableFuture[]{f0, f1, f2, f3, f4, f5};
        CompletableFuture.allOf(futures).join();
        return futures;
    }

    public List<GlRechargeAllCollect> getAllCollect(RechargeQueryDto queryDto) throws GlobalException {
        List<GlRechargeAllCollect> list = Lists.newArrayList();
        //统计充值成功总记录数&总到账金额
        if (null != queryDto.getAgentType() && queryDto.getAgentType() == -1) {
            queryDto.setAgentType(null);
        }
        if ((queryDto.getStartTime() == null || queryDto.getEndTime() == null)
                && StringUtils.isEmpty(queryDto.getOrderId())
                && StringUtils.isEmpty(queryDto.getUserName())) {
            throw new GlobalException("必填参数丢失");
        }
        List<Integer> status = queryDto.getOrderStatus();
        boolean isEmpty = setQueryParams(queryDto);
        List<GlRechargeCollect> rechargeDatas = Lists.newArrayList();
        List<GlRechargeAllCollect> rechargeCollects = Lists.newArrayList();
        List<GlRechargeCollect> arrivalDatas = Lists.newArrayList();
        List<GlRechargeAllCollect> arrivalCollects = Lists.newArrayList();


        Map<String, DigitalCoinEnum> coinEnumMap = DigitalCoinEnum.getCoinMap().entrySet().stream()
                .filter(item -> item.getValue().getIsEnable())
                .filter(item -> (StringUtils.isEmpty(queryDto.getCoinCode())
                        || queryDto.getCoinCode().equals("-1")
                        || queryDto.getCoinCode().equals(item.getKey())))
                .collect(Collectors.toMap(p  -> p.getKey(),p -> p.getValue()));

        // 按查询条件查询充值金额
        if (isEmpty) {
            for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                GlRechargeAllCollect rechargeDate = new GlRechargeAllCollect();
                rechargeDate.setDepositAmountCollect(BigDecimal.ZERO);
                rechargeDate.setHandlingFeeAmountCollect(BigDecimal.ZERO);
                rechargeDate.setCoinCode(entry.getKey());
                rechargeDate.setCount(0);
                rechargeCollects.add(rechargeDate);
            }
        } else {
            rechargeDatas = glRechargeMapper.findRechargeRecordAmount(queryDto);
            for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                List<GlRechargeCollect> tempList = rechargeDatas.stream().filter(item -> item.getCoinCode().equals(entry.getKey())).collect(Collectors.toList());
                GlRechargeAllCollect glRechargeAllCollect = new GlRechargeAllCollect();
                glRechargeAllCollect.setDepositAmountCollect(tempList.stream().map(item -> item.getDepositAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                glRechargeAllCollect.setHandlingFeeAmountCollect(tempList.stream().map(item -> item.getHandlingFeeAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                glRechargeAllCollect.setCoinCode(entry.getKey());
                glRechargeAllCollect.setCount(tempList.size());
                rechargeCollects.add(glRechargeAllCollect);
            }
        }
        // 查询成功的到账金额，笔数
        if (isEmpty || CollectionUtils.isEmpty(status) || status.stream().noneMatch(s -> s.equals(1) || s.equals(2))) {
            for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                GlRechargeAllCollect arrivalDate = new GlRechargeAllCollect();
                arrivalDate.setDepositAmountCollect(BigDecimal.ZERO);
                arrivalDate.setHandlingFeeAmountCollect(BigDecimal.ZERO);
                arrivalDate.setCoinCode(entry.getKey());
                arrivalDate.setCount(0);
                arrivalCollects.add(arrivalDate);
            }
        } else {
            queryDto.setSubStatus(status.stream().filter(s -> s.equals(1) || s.equals(2)).collect(Collectors.toList()));
            // 统计充值成功总记录数&总到账金额
            arrivalDatas = glRechargeMapper.findRechargeRecordPayAmount(queryDto);
            for (Map.Entry<String, DigitalCoinEnum> entry:coinEnumMap.entrySet()) {
                List<GlRechargeCollect> tempList = arrivalDatas.stream().filter(item -> item.getCoinCode().equals(entry.getKey())).collect(Collectors.toList());
                GlRechargeAllCollect arrivalCollect = new GlRechargeAllCollect();
                arrivalCollect.setDepositAmountCollect(tempList.stream().map(item -> item.getDepositAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                arrivalCollect.setHandlingFeeAmountCollect(tempList.stream().map(item -> item.getHandlingFeeAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                arrivalCollect.setCoinCode(entry.getKey());
                arrivalCollect.setCount(tempList.size());
                arrivalCollects.add(arrivalCollect);
            }
        }
        // 到账金额
        List<GlRechargeAllCollect> finalRechargeDatas = rechargeCollects;
        arrivalCollects.stream().forEach(item -> {
            item.setArrivalAmountCollect(item.getDepositAmountCollect().subtract(item.getHandlingFeeAmountCollect()));
            finalRechargeDatas.stream().filter(recharge -> recharge.getCoinCode().equals(item.getCoinCode()))
                    .findFirst().ifPresent(obj -> item.setDepositAmountCollect(obj.getDepositAmountCollect()));
            list.add(item);
        });
        return list;
    }

    @Override
    public PageInfo<GlRechargeDO> findPendingPageList(RechargePendingQueryDto queryDto) throws GlobalException {
        PageInfo<GlRechargeDO> pageInfo = new PageInfo<>();

        //根据姓名查询
        if (StringUtils.isNotEmpty(queryDto.getReallyName())) {
            List<GlUserDO> glUser = RPCResponseUtils.getData(glUserService.findByReallyName(queryDto.getReallyName()));
            if (null == glUser || glUser.size() == 0) {
                return pageInfo;
            }
            if (glUser.size() > 1) {
                List<Integer> idList = glUser.stream().map(GlUserDO::getId).collect(Collectors.toList());
                queryDto.setUserIdList(idList);
            } else {
                queryDto.setUserId(glUser.get(0).getId());
            }
        }
        //根据账户名查询
        if (StringUtils.isNotEmpty(queryDto.getUserName())) {
            RPCResponse<GlUserDO> rpcResponse = glUserService.getUserInfoByUsername(queryDto.getUserName());
            if (RPCResponseUtils.isFail(rpcResponse)) {
                return pageInfo;
            }
            queryDto.setUserId(rpcResponse.getData().getId());
        }
        pageInfo = findRechargePendingPageList(queryDto);
        return pageInfo;
    }

    private PageInfo<GlRechargeDO> findRechargePendingPageList(RechargePendingQueryDto queryDto) throws GlobalException {
        PageHelper.startPage(queryDto.getPage(), queryDto.getSize());

        if (null != queryDto.getOrderStatus() && queryDto.getOrderStatus().size() != 0) {
            List<Integer> status = new ArrayList<>();
            List<Integer> subStatus = new ArrayList<>();
            queryDto.getOrderStatus().stream().forEach(item -> {
                switch (item) {
                    case 0: // 待支付
                        status.add(ProjectConstant.RechargeStatus.PENDING);
                        break;
                    case 1: // 补单审核中
                        status.add(ProjectConstant.RechargeStatus.REVIEW);
                        break;
                    default: //2-补单审核成功、3-补单审核拒绝、4-人工拒绝补单、5-用户撤销、6-超时撤销
                        subStatus.add(item);
                        break;
                }
            });
            queryDto.setStatus(status);
            queryDto.setSubStatus(subStatus);
        }
        List<GlRecharge> result = glRechargeMapper.findRechargePendingPageList(queryDto);

        List<GlRechargeDO> rechargeDOList = DtoUtils.transformList(result, GlRechargeDO.class);
        if (null == rechargeDOList) {
            rechargeDOList = new ArrayList<>();
        }

        for (GlRechargeDO recharge : rechargeDOList) {
            GlRechargeSuccessRequest req = glRechargeSuccessRequestMapper.selectByPrimaryKey(recharge.getOrderId());
            if (null != req) {
                recharge.setSucReqAmount(req.getAmount());
                recharge.setSucReqOperator(req.getUsername());
                recharge.setSucReqTime(req.getCreateDate());
                recharge.setSucReqRemark(req.getRemark());
            }

            GlRechargeSuccessApprove glRechargeSuccessApprove = glRechargeSuccessApproveMapper.selectByPrimaryKey(recharge.getOrderId());
            if (null != glRechargeSuccessApprove) {
                recharge.setSucApvAmount(glRechargeSuccessApprove.getAmount());
                recharge.setSucApvOperator(glRechargeSuccessApprove.getUsername());
                recharge.setSucApvTime(glRechargeSuccessApprove.getCreateDate());
                recharge.setSucApvRemark(glRechargeSuccessApprove.getRemark());
            }
            BigDecimal payAmount = new BigDecimal(0);
            if (recharge.getStatus() == 1) {
                // 补单审核成功
                if (recharge.getSubStatus() != null && recharge.getSubStatus() == 2
                        && null != glRechargeSuccessApprove) {
                    payAmount = glRechargeSuccessApprove.getAmount().subtract(recharge.getFee());
                } else {
                    GlRechargePay rechargePay = glRechargePayBusiness.findById(recharge.getOrderId());
                    if (rechargePay != null) {
                        payAmount = rechargePay.getAmount().subtract(recharge.getFee());
                    }
                }
            }
            recharge.setPayAmount(payAmount);

            RPCResponse<GlUserDO> rpcUser = glUserService.findById(recharge.getUserId());
            GlUserDO glUser = RPCResponseUtils.getData(rpcUser);
            if (null != glUser) {
                recharge.setReallyName(Encryptor.builderName().doEncrypt(glUser.getReallyName()));
                recharge.setTelephone(Encryptor.builderMobile().doEncrypt(glUser.getTelephone()));
            }

            GlRechargeRelation rechargeRelation = glRechargeRelationBusiness.findById(recharge.getOrderId());
            if (null != rechargeRelation) {
                recharge.setOriginalOrderId(rechargeRelation.getRelationOrderId());
            }
            recharge.setPaymentName(FundLanguageUtils.getPaymentName(recharge.getPaymentId(), recharge.getPaymentName(), queryDto.getLanguage()));
        }
        return new PageInfo<>(rechargeDOList);
    }

    @Override
    public PageInfoExt<RechargeVO> findApprovePageList(RechargeApproveQueryDto queryDto) throws GlobalException {

        PageInfoExt<RechargeVO> result = new PageInfoExt<>();
        //根据姓名查询
        if (StringUtils.isNotEmpty(queryDto.getReallyName())) {
            List<GlUserDO> glUser = RPCResponseUtils.getData(glUserService.findByReallyName(queryDto.getReallyName()));
            if (null == glUser || glUser.size() == 0) {
                return result;
            }
            if (glUser.size() > 1) {
                List<Integer> idList = glUser.stream().map(GlUserDO::getId).collect(Collectors.toList());
                queryDto.setUserIdList(idList);
            } else {
                queryDto.setUserId(glUser.get(0).getId());
            }
        }
        //根据账户名查询
        if (StringUtils.isNotEmpty(queryDto.getUserName())) {
            RPCResponse<GlUserDO> rpcResponse = glUserService.getUserInfoByUsername(queryDto.getUserName());
            if (RPCResponseUtils.isFail(rpcResponse)) {
                return result;
            }
            queryDto.setUserId(rpcResponse.getData().getId());
        }
        result = findRechargeApprovePageList(queryDto);
        return result;
    }

    /**
     * 四步：
     * 1.充值记录列表
     * 2.填充申请信息
     * 3.填充审核信息
     * 4.填充到账金额
     * 5.填充订单关联用户相关信息
     * 6.填充关联订单信息
     */
    private PageInfoExt<RechargeVO> findRechargeApprovePageList(RechargeApproveQueryDto queryDto) throws GlobalException {
        PageHelper.startPage(queryDto.getPage(), queryDto.getSize());
        queryDto.setStartIndex((queryDto.getPage() - 1) * queryDto.getSize());
        queryDto.setEndIndex(queryDto.getPage() * queryDto.getSize());
        List<RechargeVO> result = glRechargeMapper.findRechargeApprovePageList(queryDto);
        for (RechargeVO recharge : result) {
            recharge.setPaymentName(FundConstant.paymentTypeMap.get(recharge.getPaymentId()));
            GlRechargeSuccessRequest req = glRechargeSuccessRequestMapper.selectByPrimaryKey(recharge.getOrderId());
            //填充申请信息
            if (null != req) {
                recharge.setSucReqAmount(req.getAmount());
                recharge.setSucReqOperator(req.getUsername());
                recharge.setSucReqTime(req.getCreateDate());
                recharge.setSucReqRemark(req.getRemark());
            }
            //填充审核信息
            GlRechargeSuccessApprove glRechargeSuccessApprove = glRechargeSuccessApproveMapper.selectByPrimaryKey(recharge.getOrderId());
            if (null != glRechargeSuccessApprove) {
                recharge.setSucApvAmount(glRechargeSuccessApprove.getAmount());
                recharge.setSucApvOperator(glRechargeSuccessApprove.getUsername());
                recharge.setSucApvTime(glRechargeSuccessApprove.getCreateDate());
                recharge.setSucApvRemark(glRechargeSuccessApprove.getRemark());
            }
            BigDecimal payAmount = new BigDecimal(0);

            GlRecharge dbRecharge = glRechargeMapper.selectByPrimaryKey(recharge.getOrderId());
            if (dbRecharge.getStatus() == 1) {
                // 补单审核成功 两种金额计算
                if (dbRecharge.getSubStatus() != null && dbRecharge.getSubStatus() == 2
                        && null != glRechargeSuccessApprove) {
                    payAmount = glRechargeSuccessApprove.getAmount().subtract(dbRecharge.getFee());
                } else {
                    GlRechargePay rechargePay = glRechargePayBusiness.findById(recharge.getOrderId());
                    if (rechargePay != null) {
                        payAmount = rechargePay.getAmount().subtract(dbRecharge.getFee());
                    }
                }
            }
            recharge.setPayAmount(payAmount);
            //填充用户名
            RPCResponse<GlUserDO> rpcUser = glUserService.findById(recharge.getUserId());
            GlUserDO glUser = RPCResponseUtils.getData(rpcUser);
            if (null != glUser) {
                recharge.setReallyName(Encryptor.builderName().doEncrypt(glUser.getReallyName()));
                recharge.setTelephone(Encryptor.builderMobile().doEncrypt(glUser.getTelephone()));
            }
            //填充关联订单
            GlRechargeRelation rechargeRelation = glRechargeRelationBusiness.findById(recharge.getOrderId());
            if (null != rechargeRelation) {
                recharge.setOriginalOrderId(rechargeRelation.getRelationOrderId());
            }
            recharge.setPaymentName(FundLanguageUtils.getPaymentName(recharge.getPaymentId(), recharge.getPaymentName(), queryDto.getLanguage()));
        }
        /*
        按照币别分类单号,并且加总金额
        */
        queryDto.setStartIndex(1);
        queryDto.setEndIndex(99999);
        Map<String, Object> extInfo = new HashMap<>();

        List<RechargeVO> temp = glRechargeMapper.findRechargeApprovePageList(queryDto);
        Map<String, String> totalAmt = temp.stream().collect(
                Collectors.groupingBy(RechargeVO::getCoin,
                        Collectors.mapping(RechargeVO::getOrderId, Collectors.joining(","))
                )
        );
        totalAmt.forEach((k, v) -> totalAmt.put(k,glRechargeSuccessApproveMapper.getTotalAmount(Arrays.asList(totalAmt.get(k).split(",")))));
        extInfo.put("totalSucApvAmount",totalAmt);
        extInfo.put("totalAmt",temp.stream().collect(
                Collectors.groupingBy(RechargeVO::getCoin,
                        Collectors.mapping(RechargeVO::getAmount, Collectors.summingDouble(BigDecimal::doubleValue))
                )
        ));

        PageInfoExt pageInfoExt = new PageInfoExt<>(result);
        pageInfoExt.setExtData(extInfo);
        return pageInfoExt;
    }

    public String export(RechargeQueryDto queryDto, GlAdminDO adminDO) throws GlobalException {
        String lockKey = "RECHARGE_DOWNLOAD_LOCK_" + adminDO.getUserId();
        String lockValue = redisService.get(lockKey);
        if ("1".equals(lockValue)) {
            throw new GlobalException("5分钟只能导出一次");
        }
        if (null != queryDto.getAgentType() && queryDto.getAgentType() == -1) {
            queryDto.setAgentType(null);
        }
        // 获取配置的导出限制
        long limit = dynamicKey.getExportLimit(ExportConfigEnum.RECHARGE_RECORD);
        // 预先查询一次检查命中条件的总数据量
        queryDto.setPage(1);
        queryDto.setSize(1);
        GlRechargeCollectResult<GlRechargeDO> preInfo = rechargeRecordHandler.findRechargeRecordPageList(queryDto);
        long totalRecord = preInfo.getTotal();
        if (totalRecord <= 0) {
            throw new GlobalException("没有满足条件的充值记录数据");
        }
        if (totalRecord > limit) {
            log.error("用户{}导出充值记录命中总数为{}超过了限制{}", adminDO.getUsername(), totalRecord, limit);
            throw new GlobalException("导出数据量超过限制，充值记录限制导出" + limit);
        }
        rechargeExportBusiness.downLoadList(queryDto, adminDO);
        redisService.set(lockKey, "1", 300);
        return "正在导出文件";
    }

    /**
     * 代理详情-充值记录-导出
     *
     * @param queryDto
     * @param adminId
     */
    @Async
    public void rechargeListExport(RechargeQueryDto queryDto, Integer adminId) {
        ExportFileDto fileDto = new ExportFileDto();
        fileDto.setUserId(adminId);
        fileDto.setFileName("充值记录");
        fileDto.setHeaders("充值时间,充值单号,三方单号,充值金额,手续费,到账金额,充值方式,充值商户,收款银行,收款人,订单状态,到账时间,操作端");
        fileDto.setSupplier(() -> getExportData(queryDto));
        exportFileHandler.exportFile(fileDto);
    }

    @Override
    public int findApproveTips() {
        Condition e = new Condition(GlRechargeSuccessRequest.class);
        e.createCriteria().andEqualTo("status",0);
        return glRechargeSuccessRequestMapper.selectCountByCondition(e);
    }

    private StringBuffer getExportData(RechargeQueryDto queryDto) throws GlobalException {
        int size = 2_000;
        queryDto.setPage(1);
        queryDto.setSize(size);
        GlRechargeCollectResult<GlRechargeDO> page = findRechargeRecordPageList(queryDto);
        int pages = Math.min(page.getPages(), 100);//限制上限20W;
        List<GlPayment> paymentList = paymentBusiness.findAll();
        List<GlRechargeDO> records = page.getList();
        StringBuffer data = new StringBuffer();
        for (int i = 0; i < pages; i++) {
            if (i > 0) {
                queryDto.setPage(i + 1);
                page = findRechargeRecordPageList(queryDto);
                records = page.getList();
            }
            for (GlRechargeDO record : records) {
                data.append(getExportData(record, paymentList)).append("\r\n");
            }
        }
        return data;
    }

    private StringBuffer getExportData(GlRechargeDO item, List<GlPayment> paymentList) {
        StringBuffer sb = new StringBuffer();
        //充值时间
        sb.append(DateUtils.format(item.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS)).append(",")
                //充值单号
                .append(item.getOrderId()).append(",")
                //三方订单号
                .append(item.getThirdOrderId()).append(",")
                //充值金额
                .append(BigDecimalUtils.ifNullSet0(item.getAmount())).append(",")
                //手续费
                .append(BigDecimalUtils.ifNullSet0(item.getFee())).append(",")
                //到账金额
                .append(Optional.ofNullable(item.getPayAmount()).orElse(new BigDecimal(0))).append(",");
        //充值方式
        String paymentName = "";
        if (!CollectionUtils.isEmpty(paymentList)) {
            for (GlPayment p : paymentList) {
                if (p.getPaymentId().equals(item.getPaymentId())) paymentName = p.getPaymentName();
            }
        }
        sb.append(paymentName).append(",");
        //充值商户
        sb.append(item.getMerchantName()).append(",")
                //收款银行
                .append(item.getBankName()).append(",")
                //收款人
                .append(StringUtils.isEmpty(item.getCardUsername()) ? "" : Encryptor.builderName().doEncrypt(item.getCardUsername())).append(",")
                //订单状态
                .append(this.getPayStatusName(item.getStatus(), item.getSubStatus())).append(",")
                //到账时间
                .append(DateUtils.format(item.getPayTime(), DateUtils.YYYY_MM_DD_HH_MM_SS)).append(",")
                //操作端
                .append(this.getClientTypeName(item.getClientType())).append(",");
        return sb;
    }

    /**
     * 支付状态：0：待支付，1：支付成功，2：支付失败，3：补单审核中
     *
     * @return
     */
    private String getPayStatusName(Integer status, Integer subStatus) {
        String statusName = "";
        if (ObjectUtils.isEmpty(status)) return "";
        switch (status) {
            case 0:
                statusName = "待支付";
                break;
            case 1:
                if (subStatus == 1) {
                    statusName = "支付成功";
                } else if (subStatus == 2) {
                    statusName = "补单审核成功";
                } else {
                    statusName = "支付成功";
                }
                break;
            case 2:
                if (subStatus == 6) {
                    statusName = "超时撤销";
                } else if (subStatus == 5) {
                    statusName = "用户撤销";
                } else if (subStatus == 4) {
                    statusName = "人工拒绝补单";
                } else if (subStatus == 3) {
                    statusName = "补单审核拒绝";
                } else {
                    statusName = "支付失败";
                }
                break;
            case 3:
                if (subStatus == 2) {
                    statusName = "补单审核成功";
                } else if (subStatus == 5) {
                    statusName = "用户撤销";
                } else if (subStatus == 6) {
                    statusName = "超时撤销";
                } else {
                    statusName = "补单审核中";
                }
                break;
            case 4:
                statusName = "处理中";
                break;
            default:
                break;
        }
        return statusName;
    }

    public String getClientTypeName(Integer clientType) {
        if (ObjectUtils.isEmpty(clientType)) return "";
        switch (clientType) {
            case 0:
                return "PC";
            case 1:
                return "H5";
            case 2:
                return "安卓";
            case 3:
                return "IOS";
            case 4:
                return "PAD";
        }
        return "";
    }
}
