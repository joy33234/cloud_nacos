package com.ruoyi.okx.params.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoinMark implements Serializable {

    private String coin;

    private String accountIds = "";

}
