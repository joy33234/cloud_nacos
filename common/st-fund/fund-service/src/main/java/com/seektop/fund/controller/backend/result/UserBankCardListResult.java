package com.seektop.fund.controller.backend.result;

import com.seektop.fund.model.GlWithdrawUserBankCard;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class UserBankCardListResult implements Serializable {

    private static final long serialVersionUID = 8071495093754076015L;

    private String name;
    private List<GlWithdrawUserBankCard> cardList;
}
