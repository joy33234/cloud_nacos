package com.seektop.fund.controller.backend.result;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ApproveResult implements Serializable {
    private static final long serialVersionUID = -6528724963304222105L;

    private Integer successNum;
}
