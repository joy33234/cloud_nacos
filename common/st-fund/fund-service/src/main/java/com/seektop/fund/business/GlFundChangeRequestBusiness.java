package com.seektop.fund.business;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.seektop.common.csvexport.model.Export;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.dto.RechargeRequestExportDto;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.handler.FundChangeToolHandler;
import com.seektop.fund.handler.GlFundReportHandler;
import com.seektop.fund.mapper.GlFundChangeRequestMapper;
import com.seektop.fund.mapper.GlFundUserLevelLockMapper;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.model.GlFundChangeRequest;
import com.seektop.fund.model.GlRecharge;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.user.service.GlAdminService;
import com.seektop.user.service.GlUserDataService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GlFundChangeRequestBusiness extends AbstractBusiness<GlFundChangeRequest> {

    @Resource
    private GlFundUserLevelLockMapper glFundUserLevelLockMapper;
    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;
    @Reference(retries = 2, timeout = 3000)
    private GlUserDataService glUserDataService;
    @Reference(retries = 2, timeout = 3000)
    private GlAdminService glAdminService;

    @Resource
    private GlFundChangeRequestMapper glFundChangereqMapper;
    @Resource
    private GlFundChangeApproveBusiness glFundChangeApproveBusiness;
    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;
    @Autowired
    private GlFundChangeRelationBusiness glFundChangeRelationBusiness;
    @Resource
    private GlRechargeMapper glRechargeMapper;
    @Resource
    private GlWithdrawMapper glWithdrawMapper;
    @Resource
    private GlFundReportHandler glFundReportHandler;
    @Autowired
    private FundChangeToolHandler fundChangeToolHandler;

    private final static String fundChangeDownLoadHeaders = "资金调整单号,调整关联单号,三方订单号,账户类型,账户名,调整类型,细分类型,调整金额,可提额度,需求流水金额,申请人,调整原因,申请时间,一审状态," +
            "一审人,一审时间,二审状态,二审人,二审时间,收款商户,收款商户号,原订单金额";

    public List<String> findAllCreator() {
        return glFundChangereqMapper.findAllCreator();
    }

    public List<String> findAllFirstApprover() {
        return glFundChangereqMapper.findAllFirstApprover();
    }

    public List<String> findAllSecondApprover() {
        return glFundChangereqMapper.findAllSecondApprover();
    }

    public PageInfo<GlFundChangeRequest> findChangeRequestList(RechargeRequestExportDto exportDto) {
        PageHelper.startPage(exportDto.getPage(), exportDto.getSize());
        List<GlFundChangeRequest> list = glFundChangereqMapper.findByGlFundChangeRequest(exportDto.getStartDate(),
                exportDto.getEndDate(), exportDto.getMinAmount(), exportDto.getMaxAmount(),
                exportDto.getKeywords(), exportDto.getUserType(), exportDto.getChangeType(),
                exportDto.getStatuses(), exportDto.getCreator(), exportDto.getFirstApprover(),
                exportDto.getSecondApprover(), exportDto.getSubType(), exportDto.getOrderId(),
                exportDto.getRelationOrderId(),exportDto.getDateType());
        if(!CollectionUtils.isEmpty(list)){
            Map<String, GlRecharge> glRechargeMap = glRechargeMapper.selectByIds(list.stream().map(p -> "'" + p.getRelationOrderId() + "'")
                    .collect(Collectors.joining(","))).stream().collect(Collectors.toMap(GlRecharge::getOrderId, v -> v, (k, v) -> v));

            Map<String, GlWithdraw> glWithdrawMap = glWithdrawMapper.selectByIds(list.stream().map(p -> "'" + p.getRelationOrderId() + "'")
                    .collect(Collectors.joining(","))).stream().collect(Collectors.toMap(GlWithdraw::getOrderId, v -> v, (k, v) -> v));
            list.forEach(item ->{
                if(glRechargeMap.containsKey(item.getOrderId())){
                    GlRecharge glRecharge = glRechargeMap.get(item.getOrderId());
                    item.setMerchantCode(glRecharge.getMerchantCode());
                    item.setOriginalOrderAmount(glRecharge.getAmount());
                }
                if (glWithdrawMap.containsKey(item.getOrderId())) {
                    GlWithdraw glWithdraw = glWithdrawMap.get(item.getOrderId());
                    item.setMerchant(glWithdraw.getMerchant());
                    item.setMerchantCode(glWithdraw.getMerchantCode());
                    item.setOriginalOrderAmount(glWithdraw.getAmount());
                }
            });
        }
        return new PageInfo(list);
    }

    @Transactional(rollbackFor = {GlobalException.class, RuntimeException.class})
    public void doFundFirstApprove(String orderIds, GlFundChangeRequest request, List<GlFundChangeRequest> requests) throws GlobalException {
        log.info("批量审核调整金额申请,idstr:{},request:{}，requests = {}",orderIds, JSON.toJSONString(request), requests);
        if (StringUtils.isBlank(orderIds) || request == null)
            return;

        for (GlFundChangeRequest req : requests) {
            GlUserDO glUser = RPCResponseUtils.getData(glUserService.findById(req.getUserId()));
            if (request.getStatus() == FundConstant.ChangeReqStatus.FIRST_APPROVAL_DENY) {
                doSubCoinRecoverIfNeed(req, glUser);
            }
            // 一审拒绝并且是代理充送活动
            if (request.getStatus() == FundConstant.ChangeReqStatus.FIRST_APPROVAL_DENY && req.getSubType() == FundConstant.ChangeOperateSubType.PROXY_RECHARGE_REBATE.getValue()) {
                glFundReportHandler.reportBonusStatus(req.getOrderId(), ProjectConstant.Status.FAILED);
            }
        }

        glFundChangereqMapper.updateList(orderIds, request.getStatus(), request.getFirstApprover(), request.getFirstRemark(), request.getFirstTime());
    }

    public void newFundChangeDownload(RechargeRequestExportDto exportDto) throws GlobalException {
        Export.builder()
                .title("资金调整记录导出" + DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
                .header(fundChangeDownLoadHeaders)
                .pageSize(2000)
                .total(200000)
                .interval(100)
                .userId(exportDto.getUserId())
                .fileType("FUNDCHANGE_DOWNLOAD_LOCK")
                .build()
                .doExport(
                        (page,size)->{
                            exportDto.setPage(page);
                            exportDto.setSize(size);
                            PageInfo<GlFundChangeRequest> pageInfo = this.findChangeRequestList(exportDto);
                            return pageInfo;
                        },
                        item->{
                            StringBuffer sb = new StringBuffer();
                            // 资金调整单号
                            sb.append(item.getOrderId());
                            sb.append(",");
                            // 调整关联单号
                            sb.append(item.getRelationOrderId() == null ? "" : item.getRelationOrderId());
                            sb.append(",");
                            // 三方订单号
                            sb.append(item.getThirdOrderId() == null ? "" : item.getThirdOrderId());
                            sb.append(",");
                            // 账户类型
                            switch (item.getUserType()) {
                                case 0:
                                    sb.append("会员");
                                    break;
                                case 1:
                                    sb.append("代理");
                                    break;
                            }
                            sb.append(",");
                            // 账户名
                            sb.append(item.getUsername());
                            sb.append(",");
                            // 调整类型
                            switch (item.getChangeType()) {
                                case 1009:
                                    sb.append("加币-计入红利");
                                    break;
                                case 1018:
                                    sb.append("加币-不计入红利");
                                    break;
                                case 1011:
                                    sb.append("减币");
                                    break;
                            }
                            sb.append(",");
                            // 细分类型
                            //TODO: bb 和 ML 区别 重构
                            switch (item.getSubType()) {
                                case 1:
                                    sb.append("红包");
                                    break;
                                case 2:
                                    sb.append("活动红利");
                                    break;
                                case 3:
                                    sb.append("人工充值");
                                    break;
                                case 4:
                                    sb.append("提现失败退回");
                                    break;
                                case 5:
                                    sb.append("转账补分");
                                    break;
                                case 6:
                                    sb.append("游戏补分-贝博体育");
                                    break;
                                case 7:
                                    sb.append("游戏补分-LB彩票");
                                    break;
                                case 8:
                                    sb.append("代充返利");
                                    break;
                                case 9:
                                    sb.append("佣金调整");
                                    break;
                                case 10:
                                    sb.append("系统回扣");
                                    break;
                                case 11:
                                    sb.append("错误充值扣回(会员)");
                                    break;
                                case 13:
                                    sb.append("会员代充扣回");
                                    break;
                                case 16:
                                    sb.append("游戏补分");
                                    break;
                                case 17:
                                    sb.append("代理提现");
                                    break;
                                case 18:
                                    sb.append("错误充值扣回(代理)");
                                    break;
                                case 19:
                                    sb.append("代理活动");
                                    break;
                                case 25:
                                    sb.append("虚拟额度");
                                    break;
                            }
                            sb.append(",");
                            // 调整金额
                            sb.append(BigDecimalUtils.ifNullSet0(item.getAmount()));
                            sb.append(",");
                            // 可提额度
                            sb.append(BigDecimalUtils.ifNullSet0(item.getValidWithdraw()));
                            sb.append(",");
                            // 需求流水金额
                            sb.append(BigDecimalUtils.ifNullSet0(item.getFreezeAmount()));
                            sb.append(",");
                            // 申请人
                            sb.append(item.getCreator());
                            sb.append(",");
                            //申请原因
                            sb.append(getRemark(item.getRemark()));
                            sb.append(",");
                            // 申请时间
                            sb.append("\t").append(DateUtils.format(item.getCreateTime(), DateUtils.YYYY_MM_DD_HH_MM_SS));
                            sb.append(",");
                            // 一审状态
                            sb.append(this.setStatus(true,item.getStatus()));
                            sb.append(",");
                            // 一审人
                            sb.append(StringUtils.isNotEmpty(item.getFirstApprover()) ? item.getFirstApprover() : "");
                            sb.append(",");
                            // 一审时间
                            sb.append("\t").append(item.getFirstTime() == null ? "-": DateUtils.format(item.getFirstTime(), DateUtils.YYYY_MM_DD_HH_MM_SS));
                            sb.append(",");
                            // 二审状态
                            sb.append(this.setStatus(false,item.getStatus()));
                            sb.append(",");
                            // 二审人
                            sb.append(StringUtils.isNotEmpty(item.getSecondApprover()) ? item.getSecondApprover() : "");
                            sb.append(",");
                            // 二审时间
                            sb.append("\t").append(item.getSecondTime() == null ? "-": DateUtils.format(item.getSecondTime(), DateUtils.YYYY_MM_DD_HH_MM_SS));
                            sb.append(",");
                            // 收款商户
                            sb.append(item.getMerchant() == null ? "" : item.getMerchant());
                            sb.append(",");
                            // 收款商户号
                            sb.append("\t").append(item.getMerchantCode() == null ? "" : item.getMerchantCode());
                            sb.append(",");
                            // 原订单金额
                            sb.append(item.getOriginalOrderAmount() == null ? "" : item.getOriginalOrderAmount().toString());
                            return sb;
                        }
                );
    }

    public static String getRemark(String remark) {
        return StringEscapeUtils.escapeCsv(Iterables.getLast(Splitter.on(",").limit(3).split(remark)));
    }


    /**
     * 设置资金调整审核状态
     *
     * @param isFirstApprove
     * @param status
     */
    private String setStatus(boolean isFirstApprove,Integer status){
        String result = "";
        switch (status) {
            case 0:
                result = "待审核";
                break;
            case 1:
                if(isFirstApprove){
                    result = "一审通过";
                }else{
                    result = "待审核";
                }
                break;
            case 2:
                if(isFirstApprove){
                    result = "一审拒绝";
                }else{
                    result = "";
                }
                break;
            case 3:
                if(isFirstApprove){
                    result = "一审通过";
                }else{
                    result = "二审通过";
                }
                break;
            case 4:
                if(isFirstApprove){
                    result = "一审通过";
                }else{
                    result = "二审拒绝";
                }
                break;
        }
        return result;
    }

    /**
     * 如果是减币操作，进行如下步骤
     * 1.账户加钱
     * 2.减币上报为失败
     * 3.新增减币退回
     * @param changeRequest
     * @param user
     */
    public void doSubCoinRecoverIfNeed(GlFundChangeRequest changeRequest, GlUserDO user) {
        if (!fundChangeToolHandler.isSub(changeRequest)) {
            log.info("changeRequest is not subCoin req= {}", changeRequest);
            return;
        }

        log.info("doSubCoinRecover request = {}, user = {}", changeRequest, user);
        //账变
        if (fundChangeToolHandler.isMinus(changeRequest)) {
            changeRequest.setAmount(changeRequest.getAmount().negate());
        }
        glFundUserAccountBusiness.addBalance(changeRequest.getUserId(), changeRequest.getAmount(), DigitalCoinEnum.CNY);
        //减币失败上报
        glFundReportHandler.reportSubCoinFail(changeRequest.getOrderId());
        //减币退回上报
        glFundReportHandler.reportSubCoinReturn(user, changeRequest);
    }

}