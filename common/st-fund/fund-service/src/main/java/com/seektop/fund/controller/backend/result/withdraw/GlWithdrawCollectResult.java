package com.seektop.fund.controller.backend.result.withdraw;

import com.github.pagehelper.PageInfo;
import com.seektop.fund.vo.GlWithdrawAllCollect;
import lombok.Data;

import java.util.List;

@Data
public class GlWithdrawCollectResult<T> extends PageInfo<T> {


    /**
     * 所有数据汇总
     */
    private GlWithdrawAllCollect glWithdrawAllCollect;


    public GlWithdrawCollectResult() {
    }

    public GlWithdrawCollectResult(List<T> list) {
        super(list);
    }
}
