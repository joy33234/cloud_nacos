package com.seektop.fund.handler;

import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.MsgEnum;
import com.seektop.fund.model.GlFundChangeRequest;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 资金调整数据处理
 */
@Component
public class FundChangeToolHandler {

    @Resource
    private DynamicKey dynamicKey;

    public FundConstant.ChangeOperateSubType getActivityProfit(){
        return  "BB".equals(dynamicKey.getAppName()) ?
                FundConstant.ChangeOperateSubType.ACTIVITY_PROFIT :
                FundConstant.ChangeOperateSubType.M6_ACTIVITY_PROFIT;
    }

    public FundConstant.ChangeOperateSubType getChangeOperateSubType(GlFundChangeRequest changeRequest) {
        return FundConstant.ChangeOperateSubType.getByValue(dynamicKey.getAppName(),
                changeRequest.getChangeType(), changeRequest.getSubType());
    }

    /**
     * 是否支持输入负数扣减
     * @param changeRequest
     * @return
     */
    public boolean isMinus(GlFundChangeRequest changeRequest) {
        FundConstant.ChangeOperateSubType changeOperateSubType = getChangeOperateSubType(changeRequest);
        return isMinus(changeOperateSubType, changeRequest.getAmount());
    }

    /**
     * 是否支持负数扣减
     * @param changeOperateSubType
     * @param amount
     * @return
     */
    public boolean isMinus(FundConstant.ChangeOperateSubType changeOperateSubType, BigDecimal amount) {
        FundConstant.ChangeOperateSubType activityProfit = getActivityProfit();
        // 资金调整加币-计入红利 / 活动红利支持输入负数扣减
        return activityProfit == changeOperateSubType
                && BigDecimalUtils.lessThanZero(amount);
    }

    /**
     * 是否计入红利
     * @param changeRequest
     * @return
     */
    public boolean isBonus(GlFundChangeRequest changeRequest){
        FundConstant.ChangeOperateSubType changeOperateSubType = getChangeOperateSubType(changeRequest);
        return FundConstant.ChangeOperateType.ADD_INCLUDE_PROFIT == changeOperateSubType.getOperateType();
    }

    /**
     * 是否扣减币
     * @param changeRequest
     * @return
     */
    public boolean isSub(GlFundChangeRequest changeRequest) {
        boolean isSub = false;
        if (changeRequest.getChangeType() == MsgEnum.SubCoin.value()) {
            isSub = true;
        }
        else if (isMinus(changeRequest)) {
            isSub = true;
        }
        return isSub;
    }
}
