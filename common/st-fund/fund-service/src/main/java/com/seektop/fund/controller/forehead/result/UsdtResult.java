package com.seektop.fund.controller.forehead.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class UsdtResult implements Serializable {

    private String protocol;

    private String message;
}
