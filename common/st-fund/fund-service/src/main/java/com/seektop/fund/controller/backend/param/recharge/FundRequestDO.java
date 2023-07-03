package com.seektop.fund.controller.backend.param.recharge;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Data
@ToString
public class FundRequestDO implements Serializable {
    private List<String> invalid = Lists.newArrayList();
    private List<String> successList = Lists.newArrayList();
    private List<String> failList = Lists.newArrayList();

    private String applyType;
    private Integer userType;
}
