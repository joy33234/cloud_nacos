package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.mapper.GlWithdrawSplitMapper;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawSplit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GlWithdrawSplitBusiness extends AbstractBusiness<GlWithdrawSplit> {
    @Resource
    private GlWithdrawSplitMapper glWithdrawSplitMapper;
    @Resource
    private GlWithdrawMapper glWithdrawMapper;

    public List<GlWithdrawSplit> findWithdrawSplitByTime(Date startDate, Date endDate) {
        return glWithdrawSplitMapper.findWithdrawSplitByTime(startDate, endDate);
    }

    public Integer getTodayWithdrawSplitCount(Integer userId, Date startDate, Date endDate) {
        Condition condition = new Condition(GlWithdraw.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("userId", userId);
        criteria.andGreaterThanOrEqualTo("createDate", startDate);
        criteria.andLessThanOrEqualTo("createDate", endDate);
        List<GlWithdraw> glWithdrawList = glWithdrawMapper.selectByCondition(condition);
        int count = 0;
        Map<String, String> parentId = new HashMap<>();
        if (glWithdrawList != null && glWithdrawList.size() != 0) {
            for (GlWithdraw glWithdraw : glWithdrawList) {
                GlWithdrawSplit split = glWithdrawSplitMapper.selectByPrimaryKey(glWithdraw.getOrderId());
                if (split != null) { //拆单
                    if (!parentId.containsKey(split.getParentId())) {
                        parentId.put(split.getParentId(), split.getParentId());
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public List<GlWithdrawSplit> findAllSplitOrderByOrderId(String orderId) {
        return glWithdrawSplitMapper.findAllSplitOrderByOrderId(orderId);
    }
}
