package com.seektop.fund.dto.param.account;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ReduceRequestDto implements Serializable {

    private static final long serialVersionUID = -5565407049920516126L;

    private List<ReduceLogDto> logs;

    private Integer adminUserId;
    private String adminUsername;
}
