package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawLevelConfigListDto implements Serializable {

        private Date startTime;
        private Date endTime;
        private Integer levelId = -1;
        private Integer page = 0;
        private Integer size = 10;
        private String coinCode = "-1";

}
