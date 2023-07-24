package com.seektop.fund.controller.backend;

import com.alibaba.fastjson.JSONArray;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.data.ReportPageResult;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawUserCheckBusiness;
import com.seektop.fund.business.withdraw.WithdrawExceptionBusiness;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawPolicyConfig;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawUserCheckConfig;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawDO;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawListResult;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawExceptionApproveDto;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawLevelConfigListDto;
import com.seektop.fund.controller.backend.param.withdraw.WithdrawExceptionDo;
import com.seektop.fund.controller.backend.result.ApproveResult;
import com.seektop.fund.handler.WithdrawExceptionHandler;
import com.seektop.fund.model.GlWithdrawLevelConfig;
import com.seektop.fund.model.GlWithdrawUserCheck;
import com.seektop.fund.vo.WithdrawExceptionQueryDto;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/manage/fund/withdraw/exception", method = RequestMethod.POST, produces = "application/json;charset=utf-8")
public class WithdrawExceptionController extends FundBackendBaseController {

    @Resource
    private RedisService redisService;

    @Resource
    private WithdrawExceptionBusiness withdrawExceptionBusiness;
    @Resource
    private GlWithdrawUserCheckBusiness glWithdrawUserCheckBusiness;

    @Resource
    private WithdrawExceptionHandler withdrawExceptionHandler;

    /**
     * 风控提款审核，待审核，审核搁置，全部列表
     * @param queryDto
     * @param admin
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/list")
    public Result list(WithdrawExceptionQueryDto queryDto,
                       @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin,
                       @RequestHeader(defaultValue = "51") Integer systemId)
            throws GlobalException {
        queryDto.setSystemId(systemId);
        PageInfo<GlWithdrawListResult> pageInfo = withdrawExceptionHandler.findList(queryDto, admin);
        return Result.genSuccessResult(pageInfo);
    }

    /**
     * 风控提款审核，待审核，审核搁置，全部导出
     * @param queryDto
     * @param admin
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/download")
    public Result download(WithdrawExceptionQueryDto queryDto,
                           @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin,
                           @RequestHeader(defaultValue = "51") Integer systemId)
            throws GlobalException {
        queryDto.setSystemId(systemId);
        withdrawExceptionHandler.download(queryDto, admin);
        return Result.genSuccessResult("正在导出，请稍后下载");
    }

    /**
     * 审核
     * @param approveDto
     * @param admin
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/approve")
    public Result approve(WithdrawExceptionApproveDto approveDto,
                          @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO admin,
                          @RequestHeader(defaultValue = "51") Integer systemId)
            throws GlobalException {
        approveDto.setSystemId(systemId);
        ApproveResult result = withdrawExceptionHandler.approve(approveDto, admin);
        return Result.genSuccessResult(result);
    }

    /**
     * 获取提现风控订单数据
     *
     * @param orderId
     * @param adminDO
     * @return
     * @throws GlobalException
     */
    @RequestMapping("/info")
    public Result info(@RequestParam String orderId,
                       @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO, ParamBaseDO paramBaseDO) throws GlobalException {
        GlWithdrawDO withdrawDO = withdrawExceptionBusiness.info(orderId, adminDO);
        return Result.newBuilder().success().addData(withdrawDO).build();
    }

    /**
     * 会员提现风控 新增
     */
    @PostMapping(value = "/level/config/add", produces = "application/json;charset=utf-8")
    public Result addLevelConfig(@Validated WithdrawExceptionDo withdrawExceptionDo,
                                 @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        withdrawExceptionBusiness.addLevelConfig(withdrawExceptionDo, adminDO);
        return Result.genSuccessResult();
    }

    /**
     * 会员提现风控 编辑
     */
    @PostMapping(value = "/level/config/update", produces = "application/json;charset=utf-8")
    public Result updateLevelConfig(@Validated WithdrawExceptionDo withdrawExceptionDo,
                                    @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        withdrawExceptionBusiness.updateLevelConfig(withdrawExceptionDo, adminDO);
        return Result.genSuccessResult();
    }

    /**
     * 会员提现风控 启用/禁用
     */
    @PostMapping(value = "/level/config/status", produces = "application/json;charset=utf-8")
    public Result levelConfigStatus(@RequestParam Integer id,
                                    @RequestParam Integer status,
                                    @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        withdrawExceptionBusiness.levelConfigStatus(id, status, adminDO);
        return Result.genSuccessResult();
    }

    /**
     * 会员提现风控 删除
     */
    @PostMapping(value = "/level/config/delete", produces = "application/json;charset=utf-8")
    public Result deleteLevelConfig(@RequestParam Integer id,
                                    @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) throws GlobalException {
        withdrawExceptionBusiness.deleteLevelConfig(id, adminDO);
        return Result.newBuilder().success().build();
    }

    /**
     * 会员提现风控 列表
     */
    @PostMapping(value = "/level/config/list", produces = "application/json;charset=utf-8")
    public Result listLevelConfig(WithdrawLevelConfigListDto dto) {
        PageInfo<GlWithdrawLevelConfig> result = withdrawExceptionBusiness.listLevelConfig(dto);
        return Result.newBuilder().success().addData(result).build();
    }

    /**
     * 提现风控设置
     */
    @PostMapping("/detail/policy")
    public Result detailPolicy() throws GlobalException {
        GlWithdrawPolicyConfig config = redisService.getHashObject(RedisKeyHelper.WITHDRAW_POLICY_CONFIG, "POLICY", GlWithdrawPolicyConfig.class);
        return Result.genSuccessResult(config);
    }

    /**
     * 提现风控设置保存
     */
    @PostMapping(value = "/save/policy", produces = "application/json;charset=utf-8")
    public Result savePolicy(GlWithdrawPolicyConfig glWithdrawPolicyConfig) {
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_POLICY_CONFIG, "POLICY", glWithdrawPolicyConfig);
        return Result.newBuilder().success().build();
    }

    /**
     * 会员风控抽检设置保存
     */
    @PostMapping(value = "/check/setConfig", produces = "application/json;charset=utf-8")
    public Result setConfig(@RequestBody String body) {
        List<GlWithdrawUserCheckConfig> configs = JSONArray.parseArray(body, GlWithdrawUserCheckConfig.class);
        String save = glWithdrawUserCheckBusiness.save(configs);
        return Result.newBuilder().success().setMessage(save).build();
    }
    /**
     * 会员风控抽检设置list
     */
    @PostMapping(value = "/check/config", produces = "application/json;charset=utf-8")
    public Result configs() {
        return Result.newBuilder().success().addData(glWithdrawUserCheckBusiness.settings()).build();
    }
    /**
     * 会员风控抽检list
     */
    @PostMapping(value = "/check/list", produces = "application/json;charset=utf-8")
    public Result checkList(Long stime, Long etime, String userName, String riskApprover, String status, Integer page , Integer size) {
        List<Integer> statuss = new ArrayList<>();
        if (status!=null) {
            for (String stat:status.split(",")){
                statuss.add(Integer.valueOf(stat));
            }
        }
        ReportPageResult<GlWithdrawUserCheck> list = glWithdrawUserCheckBusiness.list(stime,etime,userName,riskApprover, statuss, page, size);

        return Result.newBuilder().success().addData(list).build();
    }
    /**
     * 会员风控抽检save
     */
    @PostMapping(value = "/check/save", produces = "application/json;charset=utf-8")
    public Result check(String remark, String tag, int status, @RequestParam Integer id,
                        @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) {
        glWithdrawUserCheckBusiness.check(id,status,tag,remark,adminDO.getUsername());
        return Result.newBuilder().success().build();
    }
}
