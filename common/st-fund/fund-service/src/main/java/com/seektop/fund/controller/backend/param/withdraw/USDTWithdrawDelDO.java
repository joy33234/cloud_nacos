package com.seektop.fund.controller.backend.param.withdraw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class USDTWithdrawDelDO implements Serializable {
    @NotNull
    private Integer id;

    @NotNull
    private Integer userId;

    @NotBlank
    private String username;

    @NotNull
    private Integer userType;

    @NotBlank
    private String address;

    @NotBlank
    private String remark;
}
