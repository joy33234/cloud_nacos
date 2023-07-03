package com.seektop.fund.business;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.google.common.collect.Lists;
import com.seektop.agent.service.ProxyService;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.GameOrderPrefix;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.recharge.GlRechargeReceiveInfoBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.controller.backend.param.recharge.FundRequestAddDto;
import com.seektop.fund.controller.backend.param.recharge.FundRequestDO;
import com.seektop.fund.dto.param.account.ReduceLogDto;
import com.seektop.fund.dto.param.account.ReduceRequestDto;
import com.seektop.fund.handler.FundChangeToolHandler;
import com.seektop.fund.model.GlFundChangeRequest;
import com.seektop.fund.model.GlRecharge;
import com.seektop.fund.model.GlRechargeReceiveInfo;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
public class FundRequestBusiness {
    @Resource
    private GlRechargeBusiness glRechargeBusiness;
    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;
    @Resource
    private RedisService redisService;
    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;
    @Reference(retries = 2, timeout = 3000)
    private ProxyService proxyService;
    @Resource
    private DynamicKey dynamicKey;

    @Value("${fund.adjust.limit:}")
    private String fundLimitConfig;
    @Autowired
    private FundChangeToolHandler fundChangeToolHandler;
    @Resource
    private GlRechargeReceiveInfoBusiness rechargeReceiveInfoBusiness;

    public Map<Integer, Integer> getFundLimitConfig() {
       log.info("fundLimitConfig = {}", fundLimitConfig);
       if(StringUtils.isEmpty(fundLimitConfig)){
           return new HashMap<>();
       }
       Map<Integer, Integer> map = JSON.parseObject(fundLimitConfig, new TypeReference<Map<Integer, Integer>>(){});
       return map;
    }

    /**
     * 佣金調整支持3000萬
     * 系統扣回、代理上分扣回、虚拟额度支持100萬
     * 人工充值 支持30萬
     */
    public void validateRequestAmount(FundConstant.ChangeOperateSubType changeOperateSubType, FundRequestAddDto fundRequestAddDto) throws GlobalException {
        // 资金调整加币-计入红利/活动红利支持输入负数扣减
        BigDecimal amount = fundRequestAddDto.getAmount();
        BigDecimal freezeAmount = fundRequestAddDto.getFreezeAmount();
        if (fundChangeToolHandler.isMinus(changeOperateSubType, amount)) {
            if (BigDecimalUtils.moreThanZero(freezeAmount)) {
                throw new GlobalException(ResultCode.DATA_ERROR, "资金调整异常,请输入正确金额和需求流水金额");
            }
        }
        else {
            if (BigDecimalUtils.lessThanZero(amount)) {
                throw new GlobalException(ResultCode.DATA_ERROR, "资金调整异常,请输入正确金额");
            }
            else if (BigDecimalUtils.lessThanZero(freezeAmount)) {
                throw new GlobalException(ResultCode.DATA_ERROR, "需求流水金额异常:不允许输入负数");
            }
        }
        //提现额度不能大于调整金额
        if (fundRequestAddDto.getVaildWithdraw() != null) {
            fundRequestAddDto.setVaildWithdraw(fundRequestAddDto.getVaildWithdraw().compareTo(amount) == 1 ? amount : fundRequestAddDto.getVaildWithdraw());
        }

        //不同系统校验方式不一样
        Integer value = changeOperateSubType.getValue();
        Integer limit = getFundLimitConfig().get(value);
        if(limit == null){ //默认值，和 5w比较
            if (amount.compareTo(BigDecimal.valueOf(50_000)) == 1) {
                throw new GlobalException(ResultCode.DATA_ERROR, "最大调整金额5万");
            }
        }
        else{
            //配置文件中已经配置
            if (amount.compareTo(BigDecimal.valueOf(limit)) == 1) {
                int w = limit / 10000;
                throw new GlobalException(ResultCode.DATA_ERROR, "最大调整金额" + w +"万");
            }
        }

        GlRecharge glRecharge = null;
        GlWithdraw glWithdraw = null;
        if (org.springframework.util.StringUtils.hasText(fundRequestAddDto.getRelationOrderId())) {
            glRecharge = glRechargeBusiness.findById(fundRequestAddDto.getRelationOrderId());
            if (glRecharge == null) {
                glWithdraw = glWithdrawBusiness.findById(fundRequestAddDto.getRelationOrderId());
                if (glWithdraw != null && StringUtils.isNotEmpty(fundRequestAddDto.getThirdOrderId())
                        && !glWithdraw.getThirdOrderId().equals(fundRequestAddDto.getThirdOrderId())) {
                    throw new GlobalException(ResultCode.DATA_ERROR, "关联订单号与三方订单号不匹配");
                }
            } else {
                GlRechargeReceiveInfo receiveInfo = rechargeReceiveInfoBusiness.findById(glRecharge.getOrderId());
                if (receiveInfo == null ||
                        (StringUtils.isNotEmpty(fundRequestAddDto.getThirdOrderId()) &&  !receiveInfo.getThirdOrderId().equals(fundRequestAddDto.getThirdOrderId()))) {
                    throw new GlobalException(ResultCode.DATA_ERROR, "关联订单号与三方订单号不匹配");
                }
            }

            if (glRecharge == null && glWithdraw == null) {
                throw new GlobalException(ResultCode.DATA_ERROR, "关联订单号不存在");
            }
        }

        if (fundRequestAddDto.getSubType() == FundConstant.ChangeOperateSubType.PROXY_RECHARGE_REBATE.getValue()
                && (ObjectUtils.isEmpty(fundRequestAddDto.getActId()) || ObjectUtils.isEmpty(fundRequestAddDto.getRechargeAmount()))) {
            throw new GlobalException(ResultCode.DATA_ERROR, "活动ID和充值金额不能为空");
        }
    }

    public List<GlFundChangeRequest> checkType(FundRequestAddDto fundRequestAddDto, GlAdminDO adminDO,
                                               FundConstant.ChangeOperateSubType changeOperateSubType,
                                               FundRequestDO doResults) throws GlobalException {
        List<GlFundChangeRequest> requestList = Lists.newArrayList();
        for (String username : fundRequestAddDto.getUsers()) {
            RPCResponse<GlUserDO> rpcResponse = glUserService.getUserInfoByUsername(username);
            if (RPCResponseUtils.isFail(rpcResponse)) {
                doResults.getInvalid().add(username);
                continue;
            }
            GlUserDO glUserDO = rpcResponse.getData();

            //错误代充扣回 BB新校验
            if ("BB".equals(dynamicKey.getAppName())) {
                if (changeOperateSubType == FundConstant.ChangeOperateSubType.DEDUCT_WITH_PROXY_POINT) {
                    if (ObjectUtils.isEmpty(glUserDO.getParentId())
                            || ObjectUtils.isEmpty(proxyService.findByUid(glUserDO.getParentId()))) {
                        doResults.getInvalid().add(username);
                        continue;
                    }
                }
            }

            //校验用户类型和资金调整类型是否匹配
            Boolean flag = checkApplyType(fundRequestAddDto.getChangeType(), fundRequestAddDto.getSubType(), glUserDO, doResults);
            if (flag) {
                List<FundConstant.ChangeOperateSubType> subTypes = new ArrayList<>();
                if ("BB".equals(dynamicKey.getAppName())) {
                    subTypes.add(FundConstant.ChangeOperateSubType.VIRTUAL_AMOUNT);
                    subTypes.add(FundConstant.ChangeOperateSubType.DEDUCT_WITH_VIRTUAL_AMOUNT);
                } else {
                    subTypes.add(FundConstant.ChangeOperateSubType.M6_VIRTUAL_AMOUNT);
                    subTypes.add(FundConstant.ChangeOperateSubType.M6_DEDUCT_WITH_VIRTUAL_AMOUNT);
                }
                if (subTypes.stream().anyMatch(s -> s == changeOperateSubType)) {
                    if (!glUserDO.getIsFake().equals("1")) {
                        String message = "账户:" + glUserDO.getUsername() + "不是虚拟用户";
                        throw new GlobalException(ResultCode.DATA_ERROR, message);
                    }
                }
                GlFundChangeRequest request = new GlFundChangeRequest();
                request.setAmount(fundRequestAddDto.getAmount());
                request.setFreezeAmount(fundRequestAddDto.getFreezeAmount());
                request.setChangeType(fundRequestAddDto.getChangeType());
                request.setCreateTime(new Date());
                request.setCreator(adminDO.getUsername());
                request.setRemark(fundRequestAddDto.getRemark());
                request.setOrderId(redisService.getTradeNo(GameOrderPrefix.GAME_CZ.getCode()));
                setRemark(request, fundRequestAddDto.getChangeType(), fundRequestAddDto.getAmount());
                request.setStatus(0);
                request.setUserId(glUserDO.getId());
                request.setUsername(glUserDO.getUsername());
                request.setUserType(glUserDO.getUserType());
                request.setSubType(fundRequestAddDto.getSubType());
                request.setValidWithdraw(fundRequestAddDto.getVaildWithdraw());
                request.setActId(fundRequestAddDto.getActId());
                request.setRechargeAmount(fundRequestAddDto.getRechargeAmount());
                requestList.add(request);
            }
        }
        return requestList;
    }

    /**
     * 减币处理数据
     * @param dto
     * @return
     */
    public List<GlFundChangeRequest> getRequestList(ReduceRequestDto dto){
        // 减币-系统扣回
        FundConstant.ChangeOperateSubType changeOperateSubType = FundConstant.ChangeOperateSubType
                .getByValue(dynamicKey.getAppName(), FundConstant.ChangeOperateType.REDUCE, 10);
        List<ReduceLogDto> logs = dto.getLogs();
        List<GlFundChangeRequest> requestList = new ArrayList<>(logs.size());
        String remark = "%s转账异常，中心钱包多加币，转账单号%s";
        Date now = new Date();
        for (ReduceLogDto log : logs) {
            GlFundChangeRequest request = new GlFundChangeRequest();
            request.setAmount(log.getAmount());
            request.setFreezeAmount(BigDecimal.ZERO);
            request.setChangeType(changeOperateSubType.getOperateType());
            request.setSubType(changeOperateSubType.getValue());
            request.setCreateTime(now);
            request.setCreator(dto.getAdminUsername());
            request.setRemark(String.format(remark, log.getChannelName(), log.getTradeId()));
            request.setOrderId(redisService.getTradeNo(GameOrderPrefix.GAME_CZ.getCode()));
            request.setRelationOrderId(log.getTradeId());
            request.setStatus(0);
            request.setUserId(log.getUserId());
            request.setUsername(log.getUsername());
            request.setUserType(log.getUserType());
            requestList.add(request);
        }
        return requestList;
    }

    private void setRemark(GlFundChangeRequest relationOrder, Integer changeType, BigDecimal amount) {
        // 操作类型 状态码：1009 |加币-计入红利，1018|加币-不计红利，1011|减币
        if (StringUtils.isEmpty(relationOrder.getRemark())) {
            return;
        }
        StringBuffer remark = new StringBuffer();
        if (changeType == 1009 || changeType == 1018) {
            remark.append("加币");
        } else if (changeType == 1011) {
            remark.append("减币");
        }
        remark.append(",").append(amount).append(",").append(relationOrder.getRemark());
        relationOrder.setRemark(remark.toString());
    }

    private boolean checkApplyType(Integer changeType, Integer subType, GlUserDO user, FundRequestDO results) {
        Integer userType = user.getUserType();
        String username = user.getUsername();
        String applyType = "";
        boolean flag = true;

        FundConstant.ChangeOperateSubType changeOperateSubType = FundConstant.ChangeOperateSubType.getByValue(dynamicKey.getAppName(), changeType, subType);
        if (changeOperateSubType.getUserType() != -1) { //-1不需要校验用户类型
            if (userType != changeOperateSubType.getUserType()) { //用户类型不匹配
                results.getFailList().add(username);
                //aa
                if ("BB".equals(dynamicKey.getAppName())) {
                    if (changeOperateSubType == FundConstant.ChangeOperateSubType.ADD_POINT_BB_SPORT) {//单独处理
                        applyType = "加币-不计红利-游戏补分-" + dynamicKey.getAppName() + "体育";
                    }
                } else {
                    if (changeOperateSubType == FundConstant.ChangeOperateSubType.M6_ADD_POINT_BB_SPORT) {//单独处理
                        applyType = "加币-不计红利-游戏补分-" + dynamicKey.getAppName() + "体育";
                    }
                }

                flag = false;
                results.setApplyType(applyType);
                results.setUserType(userType);
            }
        }
        if (flag) {//成功的处理
            results.getSuccessList().add(username);
        }
        return flag;
    }
}
