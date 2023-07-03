package com.seektop.fund.business.recharge;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.redis.RedisService;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.recharge.PaymentRecommandAmountEditDo;
import com.seektop.fund.controller.backend.param.recharge.PaymentRecommandAmountListDo;
import com.seektop.fund.controller.backend.result.GlPaymentResult;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.mapper.GlFundPaymentRecommendAmountMapper;
import com.seektop.fund.model.GlFundPaymentRecommendAmount;
import com.seektop.fund.model.GlFundUserlevel;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GlFundPaymentRecommendAmountBusiness extends AbstractBusiness<GlFundPaymentRecommendAmount> {

    @Autowired
    private GlFundPaymentRecommendAmountMapper mapper;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Resource
    private RedisService redisService;


    /**
     * 查询列表
     * @param listDO
     * @return
     */
    public PageInfo<GlFundPaymentRecommendAmount> page(PaymentRecommandAmountListDo listDO) {
        PageHelper.startPage(listDO.getPage(), listDO.getSize());
        PageInfo<GlFundPaymentRecommendAmount> pageInfo = new PageInfo(mapper.selectAll());
        //多语言处理
        Optional.ofNullable(pageInfo.getList()).ifPresent(list -> list.forEach(o -> {
            o.setPaymentName(FundLanguageUtils.getPaymentName(o.getPaymentId(),o.getPaymentName(),listDO.getLanguage()));
        }));
        return pageInfo;
    }

    /**
     * 更新推荐金额
     * @param editDo
     * @return
     */
    public void update(PaymentRecommandAmountEditDo editDo, String operator) throws GlobalException {
        GlFundPaymentRecommendAmount recommendAmount = mapper.selectByPrimaryKey(editDo.getPaymentId());
        if (recommendAmount == null) {
            throw new GlobalException("支付方式不存在");
        }
        List<Integer> recommendAmounts = editDo.getRecommendAmount().stream().filter(item -> ObjectUtils.isNotEmpty(item))
                .sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        recommendAmount.setLastUpdate(new Date());
        recommendAmount.setRecommendAmount(StringUtils.join(recommendAmounts,","));
        recommendAmount.setOperator(operator);
        if (mapper.updateByPrimaryKeySelective(recommendAmount) > 0) {
            updatePaymentCache(editDo.getPaymentId().intValue(), recommendAmount.getCoin(),  editDo.getRecommendAmount());
        }
    }

    /**
     * 更新商户应用中 推荐金额
     * @param paymentId
     * @param recommendAmount
     */
    private void updatePaymentCache(int paymentId, String coin, List<Integer> recommendAmount) {
        String key = "";
        List<GlFundUserlevel> userlevelList = glFundUserlevelBusiness.findAll();
        for (Integer limitType : FundConstant.PAYMENT_CACHE_LIST) {
            for (Integer clientType : ProjectConstant.CLIENT_LIST){
                if (clientType == ProjectConstant.ClientType.ALL) {
                    continue;
                }
                for (GlFundUserlevel userlevel : userlevelList) {
                    //代理不处理
                    if (userlevel.getLevelType() == 1) {
                        continue;
                    }
                    if (limitType == FundConstant.PaymentCache.NORMAL) {
                        key = RedisKeyHelper.PAYMENT_MERCHANT_APP_NORMAL_CACHE + userlevel.getLevelId();
                    } else if (limitType == FundConstant.PaymentCache.LARGE) {
                        key = RedisKeyHelper.PAYMENT_MERCHANT_APP_LARGE_CACHE + userlevel.getLevelId();
                    }
                    List<GlPaymentResult> paymentResults = redisService.getHashList(key, clientType.toString(), GlPaymentResult.class);

                    if (CollectionUtils.isNotEmpty(paymentResults)) {
                        paymentResults.stream().filter(item -> item.getPaymentId() == paymentId).forEach(item -> {
                            item.getMerchantList().stream().filter(obj -> obj.getCoin().equals(coin)).forEach(obj -> {
                                obj.setRecommendAmount(recommendAmount);
                            });
                        });
                        redisService.delHashValue(key , String.valueOf(clientType));
                        redisService.putHashValue(key , String.valueOf(clientType), paymentResults);
                    }
                }
            }
        }
    }

}
