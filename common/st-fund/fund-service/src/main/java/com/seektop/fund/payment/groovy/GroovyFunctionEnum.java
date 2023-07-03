package com.seektop.fund.payment.groovy;

public enum GroovyFunctionEnum {
    GROOVY_FUNCTION_QUERY("payQuery"),
    GROOVY_FUNCTION_PAY("pay"),
    GROOVY_FUNCTION_NOTIFY("notify"),
    GROOVY_FUNCTION_WITHDRAW_QUERY("withdrawQuery"),
    GROOVY_FUNCTION_WITHDRAW_PAY("withdraw"),
    GROOVY_FUNCTION_WITHDRAW_NOTIFY("withdrawNotify"),
    GROOVY_FUNCTION_BALANCE_QUERY("balanceQuery"),
    GROOVY_FUNCTION_RESULT("result"),
    GROOVY_FUNCTION_CANCEL("cancel"),
    GROOVY_FUNCTION_INNERPAY("innerpay"),
    GROOVY_FUNCTION_NEEDNAME("needName"),
    GROOVY_FUNCTION_NEEDCARD("needCard"),
    GROOVY_FUNCTION_SHOWTYPE("showType"),
    GROOVY_FUNCTION_PAYMENTRATE("paymentRate"),
    GROOVY_FUNCTION_WITHDRAWRATE("withdrawRate"),
    GROOVY_FUNCTION_PAYMENTS("payments"),
    ;

    private String functionName;

    GroovyFunctionEnum(String functionName) {
        this.functionName = functionName;
    }

    public String getFunctionName() {
        return functionName;
    }
}
