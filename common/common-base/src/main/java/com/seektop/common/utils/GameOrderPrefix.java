package com.seektop.common.utils;

/**
 * Created by ken on 2018/7/25.
 * 平台游戏订单前缀定义
 */
public enum GameOrderPrefix {
    GAME_AG(1, "AG"),
    GAME_EBET(2, "EBET"),
    GAME_BBIN(3, "BBIN"),
    GAME_PT(4, "PT"),
    GAME_MG(5, "MG"),
    GAME_GG(6, "GG"),
    GAME_188(7, "XJ"),
    GAME_ZZ(8, "ZZ"),
    GAME_CZ(9, "CZ"),
    GAME_YJ(10, "YJ"),
    GAME_SHARE_WALLET(2000, "STSW"),//共享钱包
    GAME_KY(9, "KY"),
    GAME_LB(8, "LB"),
    GAME_HLQP(10, "QP"),
    GAME_GMQP(11, "GM"),
    GAME_5GM_LTY(12, "LTY"),
    GAME_188_LIVE(13, "XJLV"),
    GAME_VIRTUAL_SPORT(16, "XNTY"),
    GAME_IM_PP(17, "PP"),
    GAME_IM_ESPORT(18, "ES"),
    GAME_IM_SW(19, "SW"),
    GAME_MW(23, "MW"),
    GAME_XYQP(24, "XYQP"),
    GAME_IM_VR(25, "IMVR"),
    GAME_FY_ESPORT(26, "FYES"),
    GAME_ST_LOTTERY(28, "STCP"),
    GAME_BTI(29, "BT"),
    GAME_BG(30, "BG"),
    GAME_IM_SPORT(31, "IMSB"),
    GAME_DT_SLOT(32, "DT"),
    Game_UUQP(33, "UU"),
    GAME_ST_SPORT(34, "STSP"),
    GAME_OB(35, "OB"),
    GAME_IM_EVOPLAY(37, "EP"),
    GAME_PG_SLOT(38, "PG"),
    GAME_YYQP(39, "YY"),
    GAME_VR_LOTTERY(41, "VRCP"),
    GAME_SYQP(42, "SY"),
    GAME_IM_ES(43, "IMES"),
    GAME_OB_LIVE(46, "OBLV"),
    GAME_OB_ES(48, "OBES"),
    GAME_OB_LOTTERY(49, "OBLOTT"),
    GAME_OB_SPORT(50, "OBSPORT")
    ;

    private int channel;
    private String code;

    GameOrderPrefix(int channel, String code) {
        this.channel = channel;
        this.code = code;
    }

    public int getChannel() {
        return channel;
    }

    public String getCode() {
        return code;
    }

}
