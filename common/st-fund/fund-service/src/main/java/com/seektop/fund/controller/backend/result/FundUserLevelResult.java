package com.seektop.fund.controller.backend.result;

import com.seektop.fund.model.GlFundUserLevelLock;
import com.seektop.fund.model.GlFundUserlevel;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FundUserLevelResult implements Serializable {

    /**
     * 用户绑定的层级
     */
    List<GlFundUserLevelLock> levelLocks;
    /**
     * 用户层级
     */
    List<GlFundUserlevel> levels;
    /**
     * 默认兜底层
     */
    GlFundUserlevel defaultLevel;
}
