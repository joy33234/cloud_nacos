package com.seektop.fund.controller.partner.param;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.io.Serializable;


@Getter
@Setter
@NoArgsConstructor
@ToString
public class AgencyForm implements Serializable {
    private static final long serialVersionUID = -335550772886637158L;

    @NotNull(message = "userId不能为空")
    private Integer userId;
}
