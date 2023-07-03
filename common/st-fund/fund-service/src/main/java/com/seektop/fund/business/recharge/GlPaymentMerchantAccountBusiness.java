package com.seektop.fund.business.recharge;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.DateConstant;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentBusiness;
import com.seektop.fund.business.GlPaymentChannelBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.recharge.account.*;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.mapper.GlPaymentMerchantaccountMapper;
import com.seektop.fund.model.GlPaymentChannel;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.groovy.GroovyScriptUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.seektop.constant.fund.Constants.FUND_COMMON_ON;

@Component
@Slf4j
public class GlPaymentMerchantAccountBusiness extends AbstractBusiness<GlPaymentMerchantaccount> {


    @Resource
    private RedisService redisService;
    @Resource
    private GlPaymentMerchantaccountMapper glPaymentMerchantaccountMapper;
    @Resource
    private GlPaymentMerchantAppBusiness glPaymentMerchantAppBusiness;
    @Resource
    private GlPaymentChannelBusiness glPaymentChannelBusiness;
    @Resource
    private GlPaymentBusiness glPaymentBusiness;
    @Resource
    private GlPaymentMerchantAccountModifyLogBusiness modifyLogBusiness;
    @Resource
    private GlPaymentChannelBusiness paymentChannelBusiness;
    @Resource
    private GlRechargeErrorBusiness glRechargeErrorBusiness;

    public void merchantSetting(Integer status) {
        redisService.set(RedisKeyHelper.SHOW_MERCHANT_SETTING, status == 0 ? true : false, -1);
    }

    public String getMerchantSetting() {
        String setting = redisService.get(RedisKeyHelper.SHOW_MERCHANT_SETTING);
        if (StringUtils.isEmpty(setting)) {
            setting = "true";
        }
        return setting;
    }

    public GlPaymentMerchantaccount getMerchantAccountCache(Integer merchantId) {
        String key = RedisKeyHelper.PAYMENT_MERCHANT_ACCOUNT_CACHE + DateUtils.format(new Date(), DateUtils.YYYYMMDD);
        GlPaymentMerchantaccount merchantCache = redisService.getHashObject(key, String.valueOf(merchantId), GlPaymentMerchantaccount.class);
        if (null == merchantCache) {
            merchantCache = glPaymentMerchantaccountMapper.selectByPrimaryKey(merchantId);
        }
        return merchantCache;
    }

    public List<GlPaymentMerchantaccount> getMerchantAccountCache(List<Integer> merchantIds) {
        String key = RedisKeyHelper.PAYMENT_MERCHANT_ACCOUNT_CACHE + DateUtils.format(new Date(), DateUtils.YYYYMMDD);
        List<GlPaymentMerchantaccount> list = new ArrayList<>();
        merchantIds.forEach(merchantId -> Optional.ofNullable(
                redisService.getHashObject(key, String.valueOf(merchantId), GlPaymentMerchantaccount.class))
                .ifPresent(list::add)
        );
        String unCacheIds = StringUtils.join(merchantIds.stream()
                .filter(id -> list.stream().noneMatch(a -> id.equals(a.getMerchantId())))
                .distinct().collect(Collectors.toList()), ",");
        if(StringUtils.isNotBlank(unCacheIds)) {
            list.addAll(glPaymentMerchantaccountMapper.selectByIds(unCacheIds));
        }
        return list;
    }

    public Integer getFirstMerchantId(Integer limitType) {
        return glPaymentMerchantaccountMapper.getFirstMerchantId(limitType);
    }


    public GlPaymentMerchantaccount findOne(Integer merchantId) {
        Condition con = new Condition(GlPaymentMerchantaccount.class);
        Example.Criteria criteria = con.createCriteria();
        criteria.andEqualTo("merchantId", merchantId);
        criteria.andNotEqualTo("status", 2);
        return glPaymentMerchantaccountMapper.selectOneByExample(con);
    }

    public PageInfo<GlPaymentMerchantaccount> findMerchantAccountList(MerchantAccountListDO dto) {

        PageHelper.startPage(dto.getPage(), dto.getSize());
        Condition con = new Condition(GlPaymentMerchantaccount.class);
        con.excludeProperties("script");
        Example.Criteria criteria = con.createCriteria();
        if (!ObjectUtils.isEmpty(dto.getChannelId()) && -1 != dto.getChannelId()) {
            criteria.andEqualTo("channelId", dto.getChannelId());
        }
        if (!ObjectUtils.isEmpty(dto.getMerchantCode())) {
            criteria.andEqualTo("merchantCode", dto.getMerchantCode());
        }
        if (!ObjectUtils.isEmpty(dto.getLimitType()) && dto.getLimitType() != -1) {
            criteria.andEqualTo("limitType", dto.getLimitType());
        }
        if (!ObjectUtils.isEmpty(dto.getStatus()) && -1 != dto.getStatus()) {
            criteria.andEqualTo("status", dto.getStatus());
        } else {
            criteria.andNotEqualTo("status", 2);
        }
        if (!ObjectUtils.isEmpty(dto.getEnableScript()) && -1 != dto.getEnableScript()) {
            criteria.andEqualTo("enableScript", dto.getEnableScript());
        }
        con.setOrderByClause("status asc,last_update desc");
        List<GlPaymentMerchantaccount> list = findByCondition(con);
        return new PageInfo(list);
    }


    /**
     * 更新商户缓存（同步当日充值金额、充值笔数信息）
     *
     * @param merchantIds
     */
    public void updateMerchantCache(List<Integer> merchantIds) {
        for (Integer id: merchantIds) {
            GlPaymentMerchantaccount merchantaccount = glPaymentMerchantaccountMapper.selectByPrimaryKey(id);
            String key = RedisKeyHelper.PAYMENT_MERCHANT_ACCOUNT_CACHE + DateUtils.format(new Date(), DateUtils.YYYYMMDD);
            GlPaymentMerchantaccount merchantCache = redisService.getHashObject(key, String.valueOf(merchantaccount.getMerchantId()),
                    GlPaymentMerchantaccount.class);
            if (merchantCache == null) {
                merchantaccount.setSuccessAmount(0L);
                merchantaccount.setTotal(0);
                merchantaccount.setSuccess(0);
                redisService.putHashValue(key, String.valueOf(merchantaccount.getMerchantId()), merchantaccount);
                redisService.setTTL(key, DateConstant.SECOND.DAY);
            } else {
                merchantaccount.setSuccessAmount(merchantCache.getSuccessAmount());
                merchantaccount.setTotal(merchantCache.getTotal());
                merchantaccount.setSuccess(merchantCache.getSuccess());
                redisService.delHashValue(key, String.valueOf(merchantaccount.getMerchantId()));
                redisService.putHashValue(key, String.valueOf(merchantaccount.getMerchantId()), merchantaccount);
                redisService.setTTL(key, DateConstant.SECOND.DAY);
            }
        }

    }

    /**
     * 获取充值商户成功率
     *
     * @param dto
     * @return
     */
    public PageInfo<GlPaymentMerchantaccount> findSuccessRateList(MerchantAccountListDO dto) {
        String key = RedisKeyHelper.PAYMENT_MERCHANT_ACCOUNT_CACHE + DateUtils.format(new Date(), DateUtils.YYYYMMDD);

        Condition con = new Condition(GlPaymentMerchantaccount.class);
        Example.Criteria criteria = con.createCriteria();
        criteria.andNotEqualTo("status", 2);
        if (!ObjectUtils.isEmpty(dto.getChannelId()) && -1 != dto.getChannelId()) {
            criteria.andEqualTo("channelId", dto.getChannelId());
        }
        if (!ObjectUtils.isEmpty(dto.getMerchantCode())) {
            criteria.andEqualTo("merchantCode", dto.getMerchantCode());
        }
        if (!ObjectUtils.isEmpty(dto.getCoin())) {
            criteria.andEqualTo("coin", dto.getCoin());
        }
        List<GlPaymentMerchantaccount> listData = findByCondition(con);
        if (listData == null) {
            return null;
        }
        List<GlPaymentMerchantaccount> cacheData = new ArrayList<>();
        for (GlPaymentMerchantaccount merchantAccount : listData) {
            GlPaymentMerchantaccount merchantCache = redisService.getHashObject(key, String.valueOf(merchantAccount.getMerchantId()),
                    GlPaymentMerchantaccount.class);
            if (null != merchantCache) {
                merchantAccount.setSuccessAmount(merchantCache.getSuccessAmount());
                merchantAccount.setSuccess(merchantCache.getSuccess());
                merchantAccount.setTotal(merchantCache.getTotal());
                Long leftAmount = merchantAccount.getDailyLimit() - merchantCache.getSuccessAmount();
                merchantAccount.setLeftAmount(leftAmount);
                merchantAccount.setSuccessRate(0);
                if (merchantCache.getTotal() != 0) {
                    float successRate = (float) merchantCache.getSuccess() / (float) merchantCache.getTotal() * 10000;
                    merchantAccount.setSuccessRate(Math.round(successRate));
                }
            } else {
                merchantAccount.setSuccessAmount(0L);
                merchantAccount.setSuccess(0);
                merchantAccount.setTotal(0);
                merchantAccount.setLeftAmount(merchantAccount.getDailyLimit().longValue());
                merchantAccount.setSuccessRate(0);
            }
            cacheData.add(merchantAccount);
        }
        PageInfo result = new PageInfo();
        result.setTotal(cacheData.size());

        cacheData = cacheData.stream().sorted(Comparator.comparing(GlPaymentMerchantaccount::getSuccessRate).reversed()).collect(Collectors.toList());
        if (cacheData.size() > dto.getSize()) {
            Integer startIndex = (dto.getPage() - 1) * dto.getSize() > 0 ? (dto.getPage() - 1) * dto.getSize() : 0;
            Integer endIndex = dto.getPage() * dto.getSize() > cacheData.size() ? cacheData.size() : dto.getPage() * dto.getSize();
            cacheData = cacheData.subList(startIndex, endIndex);
        }
        result.setList(cacheData);
        return result;
    }


    /**
     * 更新商户配置、商户应用
     *
     * @return
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void updateAccountAndApp(List<Integer> merchantIds, Integer status, String userName) throws GlobalException {
        List<GlPaymentMerchantaccount> merchantaccountList = new ArrayList<>();
        Date now = new Date();
        List<Integer> merchantIdList = new ArrayList<>();
        for (Integer id : merchantIds) {
            GlPaymentMerchantaccount merchantaccount = this.findById(id);
            if (null == merchantaccount) {
                throw new GlobalException("操作失败：商户不存在");
            }
            merchantaccount.setStatus(status);
            merchantaccount.setLastUpdate(now);
            merchantaccount.setLastOperator(userName);
            merchantaccountList.add(merchantaccount);

            merchantIdList.add(id);
        }
        merchantaccountList.forEach(merchant -> glPaymentMerchantaccountMapper.updateByPrimaryKeySelective(merchant));

        // 同步更新应用商户状态
        if (merchantIdList.size() > 0) {
            merchantIdList = merchantIdList.stream().distinct().collect(Collectors.toList());
            glPaymentMerchantAppBusiness.SyncOpenStatus(merchantIdList, status);
        }
    }


    public void update(GlPaymentMerchantaccount merchantaccount, String channelName, String userName) {
        Date now = new Date();
        merchantaccount.setChannelName(channelName);
        merchantaccount.setLastOperator(userName);
        merchantaccount.setLastUpdate(now);
        //不更新商户公钥和私钥
        merchantaccount.setPublicKey(null);
        merchantaccount.setPrivateKey(null);
        glPaymentMerchantaccountMapper.updateByPrimaryKeySelective(merchantaccount);
    }


    /**
     * 1、查询数据库
     * 2、赋值当日收款金额
     *
     * @param listDO
     * @return
     */
    public PageInfo<GlPaymentMerchantaccount> page(MerchantAccountListDO listDO) {
        PageInfo<GlPaymentMerchantaccount> pageInfo = findMerchantAccountList(listDO);

        String key = RedisKeyHelper.PAYMENT_MERCHANT_ACCOUNT_CACHE + DateUtils.format(new Date(), DateUtils.YYYYMMDD);

        for (GlPaymentMerchantaccount account : pageInfo.getList()) {
            GlPaymentMerchantaccount merchantCache = redisService
                    .getHashObject(key, String.valueOf(account.getMerchantId()), GlPaymentMerchantaccount.class);
            account.setSuccessAmount(0L);
            account.setPrivateKey(StringEncryptor.encryptKey(account.getPrivateKey()));
            account.setPublicKey(StringEncryptor.encryptKey(account.getPublicKey()));
            if (null != merchantCache) {
                account.setSuccessAmount(merchantCache.getSuccessAmount());
            }
        }
        return pageInfo;
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public void add(MerchantAccountAddDO addDO, GlAdminDO admin) throws GlobalException {
        GlPaymentChannel dbChannel = glPaymentChannelBusiness.findById(addDO.getChannelId());
        if (null == dbChannel) {
            throw new GlobalException("充值渠道不存在");
        }

        //保存商户信息
        GlPaymentMerchantaccount merchantaccount = DtoUtils.transformBean(addDO, GlPaymentMerchantaccount.class);
        Date now = new Date();
        merchantaccount.setChannelName(dbChannel.getChannelName());
        merchantaccount.setLastOperator(admin.getUsername());
        merchantaccount.setLastUpdate(now);
        merchantaccount.setStatus(0);
        merchantaccount.setCreateDate(now);
        merchantaccount.setCreator(admin.getUsername());

        glPaymentMerchantaccountMapper.insert(merchantaccount);

        //操作日志
        modifyLogBusiness.saveModifyLog(merchantaccount,admin, FundConstant.AccountModifyType.ADD);

    }

    /**
     * 批量更新
     *
     * @param editDO
     * @param admin
     * @return
     * @throws GlobalException
     */
    public Result batchUpdate(MerchantAccountBatchEditDO editDO, GlAdminDO admin) throws GlobalException {
        Integer status = editDO.getStatus() == 0 ? 0 : 1;

        //更新商户和应用
        updateAccountAndApp(editDO.getMerchantIds(), status, admin.getUsername());
        //更新商户缓存
        updateMerchantCache(editDO.getMerchantIds());

        //修改充值应用后，更新所有层级的充值方式
        glPaymentMerchantAppBusiness.updatePaymentCache();
        return Result.genSuccessResult();
    }


    /**
     * 1、更新商户信息
     * 2、修改额度分类或者商户号，同步到商户应用
     * 3、更新商户应用和商户信息到缓存
     *
     * @param editDO
     * @param admin
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void update(MerchantAccountEditDO editDO, GlAdminDO admin) throws GlobalException {
        GlPaymentChannel dbChannel = glPaymentChannelBusiness.findById(editDO.getChannelId());
        if (null == dbChannel) {
            throw new GlobalException("充值渠道不存在");
        }

        GlPaymentMerchantaccount originData = findById(editDO.getMerchantId());
        if (null == originData) {
            throw new GlobalException("三方入款商户不存在");
        }

        //更新商户信息
        GlPaymentMerchantaccount merchantaccount = DtoUtils.transformBean(editDO, GlPaymentMerchantaccount.class);
        update(merchantaccount, dbChannel.getChannelName(), admin.getUsername());

        //保存操作日志
        merchantaccount.setChannelName(dbChannel.getChannelName());
        modifyLogBusiness.saveModifyLog(merchantaccount,admin, FundConstant.AccountModifyType.UPDATE);

        //同步修改三方商户应用的支付类型和商户code
        if (!originData.getLimitType().equals(editDO.getLimitType()) || !originData.getMerchantCode().equals(editDO.getMerchantCode())) {
            glPaymentMerchantAppBusiness.SyncLimitType(editDO.getMerchantId(), editDO.getLimitType(), editDO.getMerchantCode());
        }

        //更新商户应用缓存
        glPaymentMerchantAppBusiness.updatePaymentCache();

    }


    /**
     * 获取商户脚本
     *
     * @param merchantId
     * @return
     * @throws GlobalException
     */
    public String getScript(Integer merchantId) throws GlobalException {
        GlPaymentMerchantaccount merchantaccount = glPaymentMerchantaccountMapper.selectByPrimaryKey(merchantId);
        if (merchantaccount == null || !Objects.equals(FUND_COMMON_ON, merchantaccount.getEnableScript())) {
            throw new GlobalException("商户不存在或未启用脚本");
        }
        return merchantaccount.getScript();
    }

    /**
     * 1、更新商户脚本
     *
     * @param editScriptDO
     * @param admin
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void updateScript(MerchantAccountEditScriptDO editScriptDO, GlAdminDO admin) throws GlobalException {

        GlPaymentChannel dbChannel = glPaymentChannelBusiness.findById(editScriptDO.getChannelId());
        if (null == dbChannel) {
            throw new GlobalException("充值渠道不存在");
        }

        GlPaymentMerchantaccount merchantaccountDB = findById(editScriptDO.getMerchantId());
        if (null == merchantaccountDB) {
            throw new GlobalException("三方入款商户不存在");
        }

        if (!Objects.equals(FUND_COMMON_ON, merchantaccountDB.getEnableScript())) {
            throw new GlobalException("入款商户未启用动态脚本");
        }

        if (StringUtils.isBlank(editScriptDO.getScript())) {
            throw new GlobalException("入款商户启用动态脚本时，脚本不能为空");
        }
        String md5Digest = MD5.md5(editScriptDO.getScript());
        //尝试加载脚本，脚本加载失败时不允许保存
        if (StringUtils.isNotBlank(editScriptDO.getScript())) {
            if (!GroovyScriptUtil.validateScript(editScriptDO.getMerchantId(), editScriptDO.getScript(), md5Digest)) {
                log.error("加载动态脚本异常 {} {}", editScriptDO.getScript());
                throw new GlobalException("加载动态脚本异常");
            }
        }

        GlPaymentMerchantaccount update = new GlPaymentMerchantaccount();
        update.setMerchantId(editScriptDO.getMerchantId());
        update.setScript(editScriptDO.getScript());
        update.setScriptSign(md5Digest);
        update.setLastOperator(admin.getUsername());
        update.setLastUpdate(new Date());
        glPaymentMerchantaccountMapper.updateByPrimaryKeySelective(update);

        //保存操作日志
        merchantaccountDB.setScript(editScriptDO.getScript());
        modifyLogBusiness.saveModifyLog(merchantaccountDB,admin, FundConstant.AccountModifyType.UPDATE_SCRIPT);
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public Result delete(Integer merchantId, GlAdminDO admin) throws GlobalException {
        GlPaymentMerchantaccount originData = findById(merchantId);
        if (null == originData) {
            throw new GlobalException("三方入款商户不存在");
        }
        Date now = new Date();
        GlPaymentMerchantaccount merchant = new GlPaymentMerchantaccount();
        merchant.setStatus(ProjectConstant.CommonStatus.DELETE);
        merchant.setMerchantId(merchantId);
        merchant.setLastOperator(admin.getUsername());
        merchant.setLastUpdate(now);
        glPaymentBusiness.updatePaymentMerchant(merchant);

        modifyLogBusiness.saveModifyLog(originData,admin, FundConstant.AccountModifyType.DELETE);

        glPaymentMerchantAppBusiness.SyncStatus(merchantId, ProjectConstant.CommonStatus.DELETE);

        glPaymentMerchantAppBusiness.updatePaymentCache();
        return Result.genSuccessResult();

    }

    public BigDecimal getPaymentUSDTRate() {
        BigDecimal rate = redisService.get(RedisKeyHelper.PAYMENT_USDT_RATE, BigDecimal.class);
        if (ObjectUtils.isEmpty(rate)) {
            log.error("获取redis 充值USDT汇率缓存异常");
            return null;
        }
        return rate;
    }

}
