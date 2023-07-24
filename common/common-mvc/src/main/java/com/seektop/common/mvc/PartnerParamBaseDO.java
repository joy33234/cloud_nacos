package com.seektop.common.mvc;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PartnerParamBaseDO extends ParamBaseDO implements Serializable {

    private Integer headerAppId;

    private Integer headerUserId;

}