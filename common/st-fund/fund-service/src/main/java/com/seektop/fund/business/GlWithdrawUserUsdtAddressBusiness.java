package com.seektop.fund.business;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.user.UserOperateType;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.backend.param.withdraw.USDTWithdrawDelDO;
import com.seektop.fund.controller.forehead.param.withdraw.UsdtAddDto;
import com.seektop.fund.controller.forehead.param.withdraw.UsdtDeleteDto;
import com.seektop.fund.dto.param.withdraw.DeleteUsdtAddressDto;
import com.seektop.fund.mapper.GlWithdrawUserUsdtAddressMapper;
import com.seektop.fund.model.GlWithdrawUserUsdtAddress;
import com.seektop.fund.vo.UserBindQueryDO;
import com.seektop.fund.vo.UserBindUsdtDO;
import com.seektop.report.user.UserOperationLogReport;
import com.seektop.system.dto.MobileValidateDto;
import com.seektop.system.service.GlSystemApiService;
import com.seektop.user.dto.GlUserManageDO;
import com.seektop.user.service.UserManageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@Slf4j
public class GlWithdrawUserUsdtAddressBusiness extends AbstractBusiness<GlWithdrawUserUsdtAddress> {

    @Resource
    private GlWithdrawUserUsdtAddressMapper glWithdrawUserUsdtAddressMapper;

    @DubboReference(timeout = 3000, retries = 3)
    private GlSystemApiService glSystemApiService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private RedisService redisService;

    @DubboReference(timeout = 3000, retries = 3)
    private UserManageService userManageService;

    @Resource
    private DynamicKey dynamicKey;

    @Resource
    private OkHttpUtil okHttpUtil;

    public Integer addressCount(Integer userId) {
        return glWithdrawUserUsdtAddressMapper.queryUserUsdtCount(userId);
    }

    public Boolean isExist(String coin, String protocol, String address) {
        return glWithdrawUserUsdtAddressMapper.isExist(coin, protocol, address);
    }

    public List<GlWithdrawUserUsdtAddress> findByUserId(Integer userId, Integer status) {
        Condition condition = new Condition(GlWithdrawUserUsdtAddress.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("userId", userId);
        if (null != status) {
            criteria.andEqualTo("status", status);
        }
        return glWithdrawUserUsdtAddressMapper.selectByCondition(condition);
    }

    public List<GlWithdrawUserUsdtAddress> findByAddress(String address, Integer status) {
        Condition condition = new Condition(GlWithdrawUserUsdtAddress.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("address", address);
        if (null != status) {
            criteria.andEqualTo("status", status);
        }
        return glWithdrawUserUsdtAddressMapper.selectByCondition(condition);
    }

    public void add(UsdtAddDto addDto, GlUserDO userDO) throws GlobalException {
        MobileValidateDto validateDto = new MobileValidateDto();
        validateDto.setType(ProjectConstant.MSG_TYPE_ADD_USDT_ADDRESS);
        validateDto.setCode(addDto.getCode());
        validateDto.setTelArea(userDO.getTelArea());
        validateDto.setMobile(userDO.getTelephone());
        if (!RPCResponseUtils.getData(glSystemApiService.mobileValidate(validateDto))) {
            throw new GlobalException(ResultCode.USDT_ERROR_CODE.getCode(), "短信验证码错误", "短信验证码错误", null);
        }
        glSystemApiService.clearCode(userDO.getTelArea(), userDO.getTelephone(), ProjectConstant.MSG_TYPE_ADD_USDT_ADDRESS);

        GlWithdrawUserUsdtAddress usdtAddress = new GlWithdrawUserUsdtAddress();
        usdtAddress.setUserId(userDO.getId());
        usdtAddress.setUserName(userDO.getUsername());
        usdtAddress.setNickName(addDto.getNickName());
        usdtAddress.setCoin(addDto.getCoin());
        usdtAddress.setProtocol(addDto.getProtocol());
        usdtAddress.setAddress(addDto.getAddress());
        usdtAddress.setStatus(0);
        usdtAddress.setCreateDate(new Date());
        usdtAddress.setSelected(0);
        usdtAddress.setUpdateDate(new Date());
        glWithdrawUserUsdtAddressMapper.insert(usdtAddress);

        //上报用户操作记录
        this.addReport(userDO, addDto);
    }

    public void delete(UsdtDeleteDto deleteDto, GlUserDO userDO) throws GlobalException {
        MobileValidateDto validateDto = new MobileValidateDto();
        validateDto.setType(ProjectConstant.MSG_TYPE_DEL_USDT_ADDRESS);
        validateDto.setCode(deleteDto.getCode());
        validateDto.setTelArea(userDO.getTelArea());
        validateDto.setMobile(userDO.getTelephone());
        if (!RPCResponseUtils.getData(glSystemApiService.mobileValidate(validateDto))) {
            throw new GlobalException(ResultCode.USDT_ERROR_CODE.getCode(), "短信验证码错误", "短信验证码错误", null);
        }
        glSystemApiService.clearCode(userDO.getTelArea(), userDO.getTelephone(), ProjectConstant.MSG_TYPE_DEL_USDT_ADDRESS);

        GlWithdrawUserUsdtAddress usdtAddress = glWithdrawUserUsdtAddressMapper.selectByPrimaryKey(deleteDto.getId());
        if (null == usdtAddress) {
            throw new GlobalException(ResultCode.USDT_ERROR_CODE.getCode(), "地址不存在", "地址不存在", null);
        }
        if (!usdtAddress.getUserId().equals(userDO.getId())) {
            throw new GlobalException(ResultCode.USDT_ERROR_CODE.getCode(), "数据异常.删除失败", "数据异常.删除失败", null);
        }

        GlWithdrawUserUsdtAddress delete = new GlWithdrawUserUsdtAddress();
        delete.setId(deleteDto.getId());
        delete.setStatus(1);
        delete.setUpdateDate(new Date());
        glWithdrawUserUsdtAddressMapper.updateByPrimaryKeySelective(delete);

        this.deleteReport(userDO, deleteDto, usdtAddress);
    }

    public void doSelect(GlWithdrawUserUsdtAddress usdtAddress) {
        List<GlWithdrawUserUsdtAddress> usdtAddressList = findByUserId(usdtAddress.getUserId(), 0);
        if (usdtAddressList == null || usdtAddressList.isEmpty()) {
            return;
        }
        Date now = new Date();

        boolean found = false;
        List<GlWithdrawUserUsdtAddress> userUsdtAddressList = new ArrayList<>(usdtAddressList.size());

        for (GlWithdrawUserUsdtAddress dbUsdt : usdtAddressList) {
            GlWithdrawUserUsdtAddress usdt = new GlWithdrawUserUsdtAddress();
            usdt.setId(dbUsdt.getId());
            usdt.setSelected(0);
            usdt.setUpdateDate(now);
            if (dbUsdt.getId().equals(usdtAddress.getId())) {
                usdt.setSelected(1);
                found = true;
            }
            userUsdtAddressList.add(usdt);
        }
        if (found == true) {
            for (GlWithdrawUserUsdtAddress userUsdtAddress : userUsdtAddressList) {
                glWithdrawUserUsdtAddressMapper.updateByPrimaryKeySelective(userUsdtAddress);
            }
        }
    }

    private void addReport(GlUserDO userDO, UsdtAddDto addDto) {
        UserOperationLogReport report = new UserOperationLogReport();
        report.setLogId(redisService.generateIncr(KeyConstant.USER.USER_OPERATION_ID));
        report.setUrl(addDto.getRequestUrl());
        report.setClientType(addDto.getHeaderOsType());
        report.setDeviceId(addDto.getHeaderDeviceId());
        report.setIp(addDto.getRequestIp());

        report.setAddress("");
        report.setCreateTime(new Date());
        report.setUserId(userDO.getId());
        report.setUsername(userDO.getUsername());
        report.setUserType(userDO.getUserType());

        String operationDes = String.format("{\"钱包名称\":\"%s\",\"钱包地址\":\"%s\"}", addDto.getNickName(), addDto.getAddress());
        report.setOptBeforeData("");
        report.setOptAfterData(operationDes);//操作后记录
        report.setOperationDesc(UserOperateType.ADD_USDT_ADDRESS.getDesc());
        report.setOperationType(UserOperateType.ADD_USDT_ADDRESS.getOptType());
        // 上报前台操作记录
        reportService.userOperationLogReport(report);
    }

    private void deleteReport(GlUserDO userDO, UsdtDeleteDto deleteDto, GlWithdrawUserUsdtAddress usdtAddress) {

        UserOperationLogReport report = new UserOperationLogReport();
        report.setLogId(redisService.generateIncr(KeyConstant.USER.USER_OPERATION_ID));
        report.setUrl(deleteDto.getRequestUrl());
        report.setClientType(deleteDto.getHeaderOsType());
        report.setDeviceId(deleteDto.getHeaderDeviceId());
        report.setIp(deleteDto.getRequestIp());

        report.setAddress("");
        report.setCreateTime(new Date());
        report.setUserId(userDO.getId());
        report.setUsername(userDO.getUsername());
        report.setUserType(userDO.getUserType());

        String operationDes = String.format("{\"钱包名称\":\"%s\",\"钱包地址\":\"%s\"}", usdtAddress.getNickName(), usdtAddress.getAddress());
        report.setOptBeforeData(operationDes);
        report.setOptAfterData("");//操作后记录
        report.setOperationDesc(UserOperateType.DELETE_USDT_ADDRESS.getDesc());
        report.setOperationType(UserOperateType.DELETE_USDT_ADDRESS.getOptType());
        // 上报前台操作记录
        reportService.userOperationLogReport(report);
    }

    public Result backendDel(USDTWithdrawDelDO usdtWithdrawDelDO, GlAdminDO glAdminDO) {

        // 检查用户是否有待审核的操作
        RPCResponse<GlUserManageDO> last = userManageService.last(usdtWithdrawDelDO.getUserId());
        if (RPCResponseUtils.isFail(last)) {
            return Result.genFailResult("服务器异常，请稍后重试");
        }
        if (null != last.getData() && (last.getData().getStatus() == 0 || last.getData().getStatus() == 1)) {
            return Result.genFailResult("该会员有未审核的操作，请先审核相关记录");
        }

        GlWithdrawUserUsdtAddress delete = new GlWithdrawUserUsdtAddress();
        delete.setId(usdtWithdrawDelDO.getId());
        delete.setStatus(2);
        delete.setUpdateDate(new Date());
        glWithdrawUserUsdtAddressMapper.updateByPrimaryKeySelective(delete);

        // 保存操作审核记录
        GlUserManageDO manage = new GlUserManageDO();
        manage.setCreateTime(new Date());
        manage.setCreator(glAdminDO.getUsername());
        manage.setOptDesc(UserOperateType.DELETE_USDT_ADDRESS.getDesc());
        manage.setOptType(UserOperateType.DELETE_USDT_ADDRESS.getOptType());
        manage.setStatus(0);
        manage.setUserId(usdtWithdrawDelDO.getUserId());
        manage.setUsername(usdtWithdrawDelDO.getUsername());
        manage.setUserType(usdtWithdrawDelDO.getUserType());
        manage.setOptData(usdtWithdrawDelDO.getId().toString());
        manage.setOptBeforeData(usdtWithdrawDelDO.getAddress());
        manage.setRemark(usdtWithdrawDelDO.getRemark());
        userManageService.saveManage(manage);
        return Result.genSuccessResult();
    }

    @Async
    public boolean doDelectUsdtAddress(DeleteUsdtAddressDto dto) {

        GlWithdrawUserUsdtAddress glWithdrawUserUsdtAddress = glWithdrawUserUsdtAddressMapper.selectByPrimaryKey(dto.getOptData());
        if (ObjectUtils.isEmpty(glWithdrawUserUsdtAddress)) {
            return false;
        }
        GlWithdrawUserUsdtAddress delete = new GlWithdrawUserUsdtAddress();
        delete.setId(Integer.parseInt(dto.getOptData()));
        delete.setStatus(1);
        delete.setUpdateDate(new Date());
        glWithdrawUserUsdtAddressMapper.updateByPrimaryKeySelective(delete);
        if (dto.isSaveManage()) {
            updateUserManage(dto);
        }
        return true;
    }

    public void updateUserManage(DeleteUsdtAddressDto dto) {
        GlUserManageDO glUserManageDO = new GlUserManageDO();
        glUserManageDO.setSecondApprover(dto.getApprover());
        glUserManageDO.setSecondRemark(dto.getRemark());
        glUserManageDO.setSecondTime(dto.getApproverTime());
        glUserManageDO.setStatus(dto.getStatus());
        glUserManageDO.setManageId(dto.getManageId());
        userManageService.updateManage(glUserManageDO);
    }

    public PageInfo<UserBindUsdtDO> usdtList(UserBindQueryDO queryDO) {
        return PageHelper
                .startPage(queryDO.getPage(),queryDO.getSize())
                .doSelectPageInfo(()->glWithdrawUserUsdtAddressMapper.usdtList(queryDO));
    }

    public Boolean setBuyUSDTRate() {
        try {
            //okex
            String url = dynamicKey.getDynamicValue(DynamicKey.Key.OKEX_USDT_URL, String.class);
            Map<String, String> params = new HashMap<>();
            params.put("t", System.currentTimeMillis() + "");
            params.put("baseCurrency", "USDT");
            params.put("quoteCurrency", "CNY");
            params.put("side", "buy");
            params.put("standard", "1");
            //买入汇率
            String buyResponse = okHttpUtil.get(url, params, getRequestHeader(), null);
            BigDecimal buyRate = getRate(buyResponse);
            log.info("buyRate:{}",buyRate);
            if (buyRate != null && BigDecimalUtils.moreThanZero(buyRate)) {
                redisService.set(RedisKeyHelper.WITHDRAW_OKEX_USDT_RATE, buyRate.setScale(4, RoundingMode.DOWN),-1);
            } else {
                return false;
            }
        } catch (Exception e) {
            log.debug("查询欧易USDT汇率买入异常:{}", e);
            return false;
        }
        return true;
    }



    public Boolean setSellUSDTRate() {
        try {
            String url = dynamicKey.getDynamicValue(DynamicKey.Key.OKEX_USDT_URL, String.class);
            Map<String, String> params = new HashMap<>();
            params.put("t", System.currentTimeMillis() + "");
            params.put("baseCurrency", "USDT");
            params.put("quoteCurrency", "CNY");
            params.put("side", "sell");
            params.put("standard", "1");

            //卖出汇率
            String sellResponse = okHttpUtil.get(url, params, getRequestHeader(), null);
            BigDecimal sellRate = getRate(sellResponse);
            log.info("sellRate:{}",sellRate);
            if (sellRate != null && BigDecimalUtils.moreThanZero(sellRate)) {
                redisService.set(RedisKeyHelper.PAYMENT_USDT_RATE, sellRate.setScale(4, RoundingMode.DOWN), -1);
            } else {
                return false;
            }
        } catch (Exception e) {
            log.debug("查询欧易USDT汇率卖出异常:{}", e);
            return false;
        }
        return true;
    }



    private static BigDecimal getRate(String response) {
        if (StringUtils.isEmpty(response)) {
            return null;
        }
        JSONObject json = JSON.parseObject(response);
        if (json == null || !json.getString("code").equals("0")) {
            return null;
        }
        JSONArray jsonArray = json.getJSONArray("data");
        JSONObject jsonObject = jsonArray.getJSONObject(0);
        if (jsonObject != null) {
            return jsonObject.getBigDecimal("price");
        }
        return null;
    }

    private GlRequestHeader getRequestHeader() {
        return GlRequestHeader.builder().action("query_USDT_RATE").build();
    }
}
