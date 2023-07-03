package com.seektop.fund.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.seektop.activity.dto.param.RechargeRebateAwardDto;
import com.seektop.activity.service.RechargeRebateService;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.data.param.TimeQueryDto;
import com.seektop.data.param.recharge.RechargeChannleQueryDto;
import com.seektop.data.service.RechargeService;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlPaymentMerchantAccountBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.recharge.GlRechargeManageBusiness;
import com.seektop.fund.business.recharge.GlRechargeTransactionBusiness;
import com.seektop.fund.common.C2COrderDetailResult;
import com.seektop.fund.dto.param.recharge.RebateAwardDto;
import com.seektop.fund.dto.param.recharge.RechargeTotalParamDO;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.dto.result.recharge.RechargeCountVo;
import com.seektop.fund.handler.C2COrderHandler;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlRecharge;
import com.seektop.fund.payment.GlRechargeCancelHandler;
import com.seektop.fund.service.GlRechargeService;
import com.seektop.fund.vo.RechargeCountVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;


/**
 * Created by CodeGenerator on 2018/03/29.
 */

@Slf4j
@DubboService(retries = 2, timeout = 3000, interfaceClass = GlRechargeService.class)
public class GlRechargeServiceImpl implements GlRechargeService {

    @Autowired
    private Map<String, GlRechargeCancelHandler> glRechargeCancelHandlerMap;

    @Resource
    private GlRechargeManageBusiness glRechargeManageBusiness;

    @Resource
    private GlRechargeTransactionBusiness glRechargeTransactionBusiness;

    @Resource
    private GlPaymentMerchantAccountBusiness glPaymentMerchantAccountBusiness;

    @Resource
    private GlRechargeBusiness glRechargeBusiness;

    @Resource
    private GlRechargeMapper glRechargeMapper;

    @DubboReference
    private RechargeService rechargeService;
    @DubboReference(timeout = 3000)
    private RechargeRebateService rechargeRebateService;
    @Resource
    private RedisService redisService;
    @Resource
    private C2COrderHandler c2COrderHandler;

    @Override
    public RPCResponse<List<GlRechargeDO>> findExpiredList(int minutes, Integer subStatus) {
        return RPCResponseUtils.buildSuccessRpcResponse(DtoUtils.transformList(glRechargeManageBusiness.findExpiredList(minutes, subStatus,null), GlRechargeDO.class));
    }

    @Override
    public RPCResponse<BigDecimal> getRechargeTotal(RechargeTotalParamDO paramDO) {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        return newBuilder.success().setData(glRechargeBusiness.getRechargeTotal(paramDO.getUserId(), paramDO.getStartTime(), paramDO.getEndTime())).build();
    }

    @Override
    public void doRechargeFailed(GlRechargeDO rechargeDO) throws GlobalException {
        //超时撤销订单
        glRechargeTransactionBusiness.doRechargeTimeOut(rechargeDO);

        GlPaymentMerchantaccount merchantaccount = glPaymentMerchantAccountBusiness.findById(rechargeDO.getMerchantId());
        if (null == merchantaccount) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        //调用三方撤销订单接口
        GlRechargeCancelHandler handler = glRechargeCancelHandlerMap.get(merchantaccount.getChannelId().toString());
        if (null != handler) {
            GlRecharge glRecharge = DtoUtils.transformBean(rechargeDO, GlRecharge.class);
            handler.cancel(merchantaccount, glRecharge);
        }

    }


    @Override
    public void doC2CRechargeFailed() throws GlobalException {

        C2CConfigDO config = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
        if (ObjectUtils.isEmpty(config)) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        List<GlRecharge> recharges = glRechargeManageBusiness.findExpiredList(config.getRechargePaymentTimeout(), null, FundConstant.PaymentChannel.C2CPay);
        List<GlRechargeDO> glRechargeDOS = DtoUtils.transformList(recharges, GlRechargeDO.class);
        for (GlRechargeDO recharge:glRechargeDOS) {
            if (ObjectUtils.isEmpty(recharge.getSubStatus())) {
                doRechargeFailed(recharge);
            }
        }
    }

    @Override
    public RPCResponse<BigDecimal> sumAmountByUserId(Integer userId) {
        Date now = new Date();
        Date startTime = DateUtils.addDay(-90, now);
        return RPCResponseUtils.buildSuccessRpcResponse(glRechargeMapper.sumAmountByUserId(userId, startTime, now));
    }

    @Override
    public RPCResponse<List<RechargeCountVo>> countGroupByPaymentId(Integer userId, Date dayStart, Date dayEnd) {
        List<RechargeCountVo> results = Lists.newArrayList();
        List<RechargeCountVO> vos = glRechargeMapper.countGroupByPaymentId(userId, dayStart, dayEnd);
        if (!ObjectUtils.isEmpty(vos)) {
            for (RechargeCountVO vo : vos) {
                RechargeCountVo temp = new RechargeCountVo();
                BeanUtils.copyProperties(vo, temp);
                results.add(temp);
            }
        }
        return RPCResponseUtils.buildSuccessRpcResponse(results);
    }

    @Override
    public RPCResponse<Date> selectFirstRechargeDate() {
        return RPCResponseUtils.buildSuccessRpcResponse(glRechargeMapper.selectFirstRechargeDate());
    }

    @Override
    public void firstRechargeReport(GlRechargeDO rechargeDO) {
        glRechargeTransactionBusiness.firstRechargeReportFix(rechargeDO);
    }

    @Override
    public RPCResponse<List<GlRechargeDO>> selectFixData(Date fixDate, Integer page, Integer size) {
        List<GlRecharge> fixList = glRechargeMapper.selectFixData(DateUtils.getMinTime(fixDate), DateUtils.getMaxTime(fixDate), page, size);
        return RPCResponseUtils.buildSuccessRpcResponse(DtoUtils.transformList(fixList, GlRechargeDO.class));
    }

    @Override
    public RPCResponse<List<GlRechargeDO>> selectRechargeData(Date startDate, Date endDate, Integer paymentId, Integer page, Integer size) {
        List<GlRecharge> fixList = glRechargeMapper.selectRechargeActivity(startDate, endDate, paymentId, page, size);
        return RPCResponseUtils.buildSuccessRpcResponse(DtoUtils.transformList(fixList, GlRechargeDO.class));
    }


    public void cleanRechargeData(Date startDate, Date endDate, Integer status) {
        //每次处理一天的数据
        int diffDay = DateUtils.diffDay(startDate, endDate);
        log.info("清理充值数据日期天数  diffDay  = {}  endDate = {}", diffDay, DateUtils.format(endDate, DateUtils.YYYY_MM_DD));
        endDate = DateUtils.getMaxTime(endDate);
        for (int i = 0; i < diffDay; i++) {
            startDate = DateUtils.getMinTime(startDate);
            while (true) {
                int count = glRechargeMapper.cleanRechargeData(startDate, endDate, status);
                log.info("清理充值数据 startDate = {}   count = {}", DateUtils.format(endDate, DateUtils.YYYY_MM_DD), count);
                if (count == 0) {
                    break;
                }
            }
            startDate = DateUtils.addDay(1, startDate);
        }
    }

    @Override
    public void rechargeDataReport(String orderId) {
        GlRecharge recharge = glRechargeMapper.selectByPrimaryKey(orderId);
        if (null == recharge) {
            return;
        }
        //充值记录重新上报
        glRechargeTransactionBusiness.rechargeDataReport(recharge);
    }

    @Override
    public void firstRechargeDataReport(GlUserDO userDO) {
        log.info("用户 {} 修复老用户首存数据上报", userDO.getUsername());
        GlRecharge recharge = glRechargeMapper.findUserFirstRecharge(userDO.getId());
        if (null == recharge) {
            log.error("用户 {} 查询充值成功记录失败", userDO.getUsername());
            return;
        }
        glRechargeTransactionBusiness.rechargeDataReport(recharge, userDO);
    }

    @Override
    public RPCResponse<Integer> fixEsData(long stime, long etime) {
        //获取es充值信息
        TimeQueryDto dto = new TimeQueryDto();
        dto.setStime(stime);
        dto.setEtime(etime);
        List<String> data = Optional.ofNullable(rechargeService.rechargeUnEndOrder(dto).getData()).orElse(Collections.EMPTY_LIST);
        for (String order:data){
            if (StringUtils.isNotBlank(order)) rechargeDataReport(order);
        }
        return RPCResponseUtils.buildSuccessRpcResponse(data.size());
    }

    @Override
    public RPCResponse<Integer> reportEsByChannelIdData(long stime, long etime, int channel) {
        //获取es充值信息
        RechargeChannleQueryDto dto = new RechargeChannleQueryDto();
        dto.setStime(stime);
        dto.setEtime(etime);
        dto.setChannel(channel);
        List<String> data = Optional.ofNullable(rechargeService.rechargeOrder(dto).getData()).orElse(Collections.EMPTY_LIST);
        for (String order:data){
            if (StringUtils.isNotBlank(order)) rechargeDataReport(order);
        }
        return RPCResponseUtils.buildSuccessRpcResponse(data.size());
    }

    @Async
    @Override
    public void rechargeRebateAward(RebateAwardDto rebateAwardDto) {
        Date startTime = rebateAwardDto.getStartTime(), endTime = rebateAwardDto.getEndTime();
        int size = 200;
        Page<Object> page = PageHelper.startPage(1, size);
        List<GlRecharge> list = glRechargeMapper.findForRebate(startTime, endTime);
        int pages = page.getPages();
        for (int i = 0; i < pages; i++) {
            if (i > 0) {
                PageHelper.startPage(i + 1, size , false);
                list = glRechargeMapper.findForRebate(startTime, endTime);
            }
            for (GlRecharge recharge : list) {
                RechargeRebateAwardDto awardDto = new RechargeRebateAwardDto();
                awardDto.setUserId(recharge.getUserId());
                awardDto.setOrderId(recharge.getOrderId());
                awardDto.setAmount(recharge.getAmount());
                awardDto.setPaymentId(recharge.getPaymentId());
                awardDto.setTime(recharge.getLastUpdate());
                rechargeRebateService.reAward(awardDto);
            }
        }
    }


    @Override
    public RPCResponse<List<GlRechargeDO>> findByOrderIds(List<String> orderIds) {
        List<GlRechargeDO> rechargeDOS = Lists.newArrayList();
        String ids = String.format("'%s'", StringUtils.join(orderIds, "','"));
        List<GlRecharge> list = glRechargeMapper.selectByIds(ids);
        for (GlRecharge recharge:list) {
            GlRechargeDO rechargeDO = DtoUtils.transformBean(recharge, GlRechargeDO.class);
            C2COrderDetailResult detailResult = c2COrderHandler.getByRechargeOrderId(recharge.getOrderId());
            Optional.ofNullable(detailResult).ifPresent(obj -> {
                rechargeDO.setPaymentDate(obj.getPaymentDate());
            });
            rechargeDOS.add(rechargeDO);
        }
        return RPCResponseUtils.buildSuccessRpcResponse(rechargeDOS);
    }
}
