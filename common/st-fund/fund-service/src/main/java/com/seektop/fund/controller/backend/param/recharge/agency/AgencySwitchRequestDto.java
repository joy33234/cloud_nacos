package com.seektop.fund.controller.backend.param.recharge.agency;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class AgencySwitchRequestDto {
    private Integer value;
    private List<Integer> open = Lists.newArrayList();
}
