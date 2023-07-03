package com.seektop.fund.controller.backend.result.recharge;

import com.github.pagehelper.PageInfo;
import com.seektop.fund.vo.GlRechargeAllCollect;
import lombok.Data;

import java.util.List;

@Data
public class GlRechargeCollectResult<T> extends PageInfo<T> {


    /**
     * 所有数据汇总
     */
    private List<GlRechargeAllCollect> glRechargeAllCollect;

    public GlRechargeCollectResult() {
    }

    public GlRechargeCollectResult(List<T> list) {
        super(list);
    }
}
