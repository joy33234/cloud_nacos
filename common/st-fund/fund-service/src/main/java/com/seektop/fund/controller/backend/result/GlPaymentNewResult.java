package com.seektop.fund.controller.backend.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlPaymentNewResult implements Serializable {


    private static final long serialVersionUID = 5364834762116574302L;

    private List<GlPaymentResult> normal;

    private List<GlPaymentResult> large;
}
