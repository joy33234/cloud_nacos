package com.seektop.fund.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ManageParamBaseDO;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.data.service.RechargeService;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.fund.business.monitor.RechargePayerMonitorNameWhiteListBusiness;
import com.seektop.fund.business.monitor.RechargePayerMonitorRecordBusiness;
import com.seektop.fund.business.monitor.RechargePayerMonitorUsernameWhiteListBusiness;
import com.seektop.fund.controller.backend.param.monitor.*;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.model.RechargePayerMonitorNameWhiteList;
import com.seektop.fund.model.RechargePayerMonitorRecord;
import com.seektop.fund.model.RechargePayerMonitorUsernameWhiteList;
import com.seektop.user.service.GlUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargePayerMonitorHandler {

    private final RechargePayerMonitorRecordBusiness rechargePayerMonitorRecordBusiness;
    private final RechargePayerMonitorNameWhiteListBusiness rechargePayerMonitorNameWhiteListBusiness;
    private final RechargePayerMonitorUsernameWhiteListBusiness rechargePayerMonitorUsernameWhiteListBusiness;

    @DubboReference(retries = 0, timeout = 5000)
    private GlUserService glUserService;
    @DubboReference(retries = 0, timeout = 5000)
    private RechargeService rechargeService;

    public Result monitorRecordList(GlAdminDO adminDO, RechargePayerMonitorRecordListParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(rechargePayerMonitorRecordBusiness.findRecordList(paramDO)).build();
        } catch (Exception ex) {
            log.error("获取充值付款人姓名监控列表发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_GET_PAYNAME_LIST_ERROR).parse(paramDO.getLanguage())).build();
        }
    }

    public Result monitorRecordCount(GlAdminDO adminDO, ManageParamBaseDO paramBaseDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(rechargePayerMonitorRecordBusiness.getTipsCount()).build();
        } catch (Exception ex) {
            log.error("获取充值付款人姓名监控总数发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_GET_PAYNAME_SUM_ERROR).parse(paramBaseDO.getLanguage())).build();
        }
    }

    public Result getTimes(GlAdminDO adminDO, ManageParamBaseDO paramBaseDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(getMonitorTimes()).build();
        } catch (Exception ex) {
            log.error("获取充值付款人姓名监控次数发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_GET_PAYNAME_COUNT_ERROR).parse(paramBaseDO.getLanguage())).build();
        }
    }

    public Result submitTimes(GlAdminDO adminDO, RechargePayerMonitorTimesParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            RedisTools.valueOperations().set(KeyConstant.FUND.RECHARGE_PAYER_NAME_TIMES_CONFIG, paramDO.getTimes());
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("保存充值付款人姓名监控次数发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_SAVE_PAYNAME_COUNT_ERROR).parse(paramDO.getLanguage())).build();
        }
    }

    public Result whiteList4Username(GlAdminDO adminDO, RechargePayerMonitorUsernameWhiteListParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(rechargePayerMonitorUsernameWhiteListBusiness.findList(paramDO)).build();
        } catch (Exception ex) {
            log.error("获取用户账号白名单列表发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USET_GET_WHITE_LIST_ERROR).parse(paramDO.getLanguage())).build();
        }
    }

    public Result submitAddWhiteList4Username(GlAdminDO adminDO, RechargePayerMonitorUsernameWhiteListSubmitParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            // 检查用户名是否正确
            RPCResponse<GlUserDO> userRPCResponse = glUserService.findByUserName(paramDO.getUsername());
            if (RPCResponseUtils.isFail(userRPCResponse)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USER_ACCOUNT_NOT_EXIST).parse(paramDO.getLanguage())).build();
            }
            GlUserDO userDO = userRPCResponse.getData();
            // 检查用户名是否已经在白名单
            RechargePayerMonitorUsernameWhiteList usernameWhiteList = rechargePayerMonitorUsernameWhiteListBusiness.findById(userDO.getId());
            if (usernameWhiteList != null) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USER_ACCOUNT_ALREADY_IN_WHITE_LIST).parse(paramDO.getLanguage())).build();
            }
            usernameWhiteList = new RechargePayerMonitorUsernameWhiteList();
            usernameWhiteList.setUserId(userDO.getId());
            usernameWhiteList.setCreator(adminDO.getUsername());
            usernameWhiteList.setCreateDate(new Date());
            rechargePayerMonitorUsernameWhiteListBusiness.save(usernameWhiteList);
            // 如果用户已经在监控列表则立即移除
            rechargePayerMonitorRecordBusiness.deleteById(userDO.getId());
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("创建用户账号白名单发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USER_ACCOUNT_CREATE_WHITE_ERROR).parse(paramDO.getLanguage())).build();
        }
    }

    public Result submitDeleteWhiteList4Username(GlAdminDO adminDO, Integer userId, ManageParamBaseDO paramBaseDo) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(userId)) {
            return newBuilder.paramError().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USER_ID_EMPTY).parse(paramBaseDo.getLanguage())).build();
        }
        try {
            RechargePayerMonitorUsernameWhiteList usernameWhiteList = rechargePayerMonitorUsernameWhiteListBusiness.findById(userId);
            if (ObjectUtils.isEmpty(usernameWhiteList)) {
                return newBuilder.paramError().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USER_ACCOUNT_WHITE_NOT_EXIST).parse(paramBaseDo.getLanguage())).build();
            }
            rechargePayerMonitorUsernameWhiteListBusiness.deleteById(usernameWhiteList.getUserId());
            return newBuilder.success().addData(usernameWhiteList).build();
        } catch (Exception ex) {
            log.error("删除用户账号白名单发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USER_ACCOUNT_WHITE_DELETE_ERROR).parse(paramBaseDo.getLanguage())).build();

        }
    }

    public Result whiteList4Name(GlAdminDO adminDO, RechargePayerMonitorNameWhiteListParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(rechargePayerMonitorNameWhiteListBusiness.findList(paramDO.getPage(), paramDO.getSize())).build();
        } catch (Exception ex) {
            log.error("获取付款人姓名白名单列表发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USET_PAYERNAME_GET_WHITE_LIST_ERROR).parse(paramDO.getLanguage())).build();
        }
    }

    public Result submitAddWhiteList4Name(GlAdminDO adminDO, RechargePayerMonitorNameWhiteListSubmitParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            String[] nameArray = paramDO.getName().split(",");
            if (nameArray.length > 100) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USET_WHITE_MAX_100).parse(paramDO.getLanguage())).build();
            }
            List<String> existNameList = Lists.newArrayList();
            List<String> successNameList = Lists.newArrayList();
            for (String name : nameArray) {
                if (rechargePayerMonitorNameWhiteListBusiness.hasExist(name)) {
                    existNameList.add(name);
                    continue;
                }
                RechargePayerMonitorNameWhiteList nameWhiteList = new RechargePayerMonitorNameWhiteList();
                nameWhiteList.setName(name);
                nameWhiteList.setCreator(adminDO.getUsername());
                nameWhiteList.setCreateDate(new Date());
                rechargePayerMonitorNameWhiteListBusiness.save(nameWhiteList);
                successNameList.add(nameWhiteList.getName());
            }
            JSONObject resultDataObj = new JSONObject();
            resultDataObj.put("existNameList", existNameList);
            resultDataObj.put("successNameList", successNameList);
            return newBuilder.success().addData(resultDataObj).build();
        } catch (Exception ex) {
            log.error("创建付款人姓名白名单发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USET_PAYERNAME_CREATE_ERROR).parse(paramDO.getLanguage())).build();

        }
    }

    public Result submitDeleteWhiteList4Name(GlAdminDO adminDO, Integer recordId, ManageParamBaseDO paramBaseDO) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(recordId)) {
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USET_PAYERNAME_ID_NOT_EMPTY).parse(paramBaseDO.getLanguage())).build();
        }
        try {
            RechargePayerMonitorNameWhiteList nameWhiteList = rechargePayerMonitorNameWhiteListBusiness.findById(recordId);
            if (ObjectUtils.isEmpty(nameWhiteList)) {
                return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USET_PAYERNAME_ID_NOT_EXIST).parse(paramBaseDO.getLanguage())).build();
            }
            rechargePayerMonitorNameWhiteListBusiness.deleteById(nameWhiteList.getId());
            return newBuilder.success().addData(nameWhiteList).build();
        } catch (Exception ex) {
            log.error("删除付款人姓名白名单发生异常", ex);
            return newBuilder.fail().setMessage(LanguageLocalParser.key(FundLanguageMvcEnum.USET_PAYERNAME_DELETE_WHITE_ERROR).parse(paramBaseDO.getLanguage())).build();
        }
    }

    @Async
    public void monitorFromTimesConfig(Integer times) {
        try {
            log.info("[充值付款人姓名监控]当配置次数变更为{}次时，准备检测监控列表中的数据", times);
            int page = 1;
            int totalSuccess = 0;
            while (true) {
                // 检查充值付款人姓名次数小于当前配置的监控用户
                List<RechargePayerMonitorRecord> monitorRecords = rechargePayerMonitorRecordBusiness.findByTimes(times, page, 100);
                if (CollectionUtils.isEmpty(monitorRecords)) {
                    break;
                }
                for (RechargePayerMonitorRecord monitorRecord : monitorRecords) {
                    // 删除监控记录
                    rechargePayerMonitorRecordBusiness.deleteById(monitorRecord.getUserId());
                    // 重新检测是否监控
                    monitorFromUserId(monitorRecord.getUserId(), null);
                    totalSuccess++;
                }
                page++;
            }
            log.info("[充值付款人姓名监控]当配置次数变更为{}次时，成功重新处理监控用户总数是{}", times, totalSuccess);
        } catch (Exception ex) {
            log.error("[充值付款人姓名监控]处理监控次数{}检测时发生异常", times, ex);
        }
    }

    @Async
    public void monitorFromPayerName(JSONArray successNameList) {
        if (CollectionUtils.isEmpty(successNameList)) {
            return;
        }
        for (int i = 0, len = successNameList.size(); i < len; i++) {
            monitorFromPayerName(successNameList.getString(i));
        }
    }

    @Async
    public void monitorFromPayerName(String name) {
        try {
            log.info("[充值付款人姓名监控]当付款人姓名{} 白名单添加时，准备检测监控列表中的数据", name);
            int page = 1;
            int totalSuccess = 0;
            while (true) {
                // 检查存在该姓名的监控记录
                List<RechargePayerMonitorRecord> monitorRecords = rechargePayerMonitorRecordBusiness.findByPayerName(name, page, 100);
                if (CollectionUtils.isEmpty(monitorRecords)) {
                    break;
                }
                for (RechargePayerMonitorRecord monitorRecord : monitorRecords) {
                    // 删除监控记录
                    rechargePayerMonitorRecordBusiness.deleteById(monitorRecord.getUserId());
                    // 重新检测是否监控
                    monitorFromUserId(monitorRecord.getUserId(), null);
                    totalSuccess++;
                }
                page++;
            }
            log.info("[充值付款人姓名监控]当付款人姓名{} 白名单添加时，成功重新处理监控用户总数是{}", name, totalSuccess);
        } catch (Exception ex) {
            log.error("[充值付款人姓名监控]处理姓名{}检测时发生异常", name, ex);
        }
    }

    @Async
    public void monitorFromUserId(Integer userId, String name) {
        try {
            // 如果用户是白名单，不进行处理
            RechargePayerMonitorUsernameWhiteList usernameWhiteList = rechargePayerMonitorUsernameWhiteListBusiness.findById(userId);
            if (usernameWhiteList != null) {
                return;
            }
            // 获取当前用户的历史付款人姓名
            RPCResponse<Set<String>> allPayerNameRPCResponse = rechargeService.allPayNameV2(userId);
            Set<String> historyPayerNameSet = Optional.ofNullable(allPayerNameRPCResponse.getData()).orElse(Collections.emptySet());
            // 查询姓名白名单
            Set<String> nameWhiteSet = rechargePayerMonitorNameWhiteListBusiness.findNameWhiteList();
            Set<String> payerNameSet = getPayerNameSet(nameWhiteSet, historyPayerNameSet, name);
            // 获取配置的监控次数
            Integer times = getMonitorTimes();
            log.info("[充值付款人姓名监控]用户{} 历史付款人姓名{} 白名单姓名{} 处理后的付款人姓名{} 配置次数{}", userId, JSON.toJSONString(historyPayerNameSet), JSON.toJSONString(nameWhiteSet), JSON.toJSONString(payerNameSet), times);
            if (payerNameSet.size() < times) {
                return;
            }
            RechargePayerMonitorRecord monitorRecord = rechargePayerMonitorRecordBusiness.findById(userId);
            if (ObjectUtils.isEmpty(monitorRecord)) {
                monitorRecord = new RechargePayerMonitorRecord();
                monitorRecord.setUserId(userId);
                monitorRecord.setTimes(payerNameSet.size());
                monitorRecord.setPayerName(JSON.toJSONString(payerNameSet));
                monitorRecord.setCreateDate(new Date());
                rechargePayerMonitorRecordBusiness.save(monitorRecord);
            } else {
                RechargePayerMonitorRecord updateMonitorRecord = new RechargePayerMonitorRecord();
                updateMonitorRecord.setUserId(userId);
                updateMonitorRecord.setTimes(payerNameSet.size());
                updateMonitorRecord.setPayerName(JSON.toJSONString(payerNameSet));
                updateMonitorRecord.setCreateDate(new Date());
                rechargePayerMonitorRecordBusiness.updateByPrimaryKeySelective(updateMonitorRecord);
            }
            log.info("[充值付款人姓名监控]用户{}满足条件进入监控列表 充值付款人姓名{} 配置的监控次数{}", userId, JSON.toJSONString(payerNameSet), times);
        } catch (Exception ex) {
            log.error("[充值付款人姓名监控]处理用户{}检测时发生异常", userId, ex);
        }
    }

    protected Integer getMonitorTimes() {
        Integer times = RedisTools.valueOperations().get(KeyConstant.FUND.RECHARGE_PAYER_NAME_TIMES_CONFIG, Integer.class);
        if (ObjectUtils.isEmpty(times)) {
            times = 2;
            RedisTools.valueOperations().set(KeyConstant.FUND.RECHARGE_PAYER_NAME_TIMES_CONFIG, times);
        }
        return times;
    }

    protected Boolean isPayerName(String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        // 检查长度是否大于1
        if (name.length() <= 1) {
            return false;
        }
        // 检查姓名是否是中文
        Pattern pattern = Pattern.compile("[\u4e00-\u9fa5]");
        return pattern.matcher(name).find();
    }

    protected Set<String> getPayerNameSet(Set<String> nameWhiteSet, Set<String> payerNameSet, String name) {
        Set<String> nameList = Sets.newHashSet();
        if (StringUtils.hasText(name) && isPayerName(name) && nameWhiteSet.contains(name) == false) {
            nameList.add(name);
        }
        if (CollectionUtils.isEmpty(payerNameSet)) {
            return nameList;
        }
        for (String payerName : payerNameSet) {
            if (isPayerName(payerName) && nameWhiteSet.contains(payerName) == false) {
                nameList.add(payerName);
            }
        }
        return nameList;
    }

}