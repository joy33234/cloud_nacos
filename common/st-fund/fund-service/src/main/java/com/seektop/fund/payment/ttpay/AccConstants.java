package com.seektop.fund.payment.ttpay;

/**
 * @author liufei
 * @version 1.0
 * @Description 账户常量
 * @Date 18/7/31 下午1:26
 **/
public interface AccConstants {

    interface SignType{
        static final String MD5 = "MD5";
        static final String RSA = "RSA";
    }

    /**
     * 产品编码
     */
    enum ProductCode{
        DF("DF","代付"),
        DK("DK","代扣"),
        KJ("KJ","快捷支付"),
        PLATFORM("PLATFORM","网关支付"),
        SCAN("SCAN","聚合扫码支付"),
        WXPAY("WXPAY","微信支付"),
        ALIPAY("ALIPAY","支付宝支付"),
        QQ_SCAN("QQ_SCAN","QQ扫码支付"),
        JDPAY("JDPAY","京东支付"),
        BANK_SCAN("BANK_SCAN","网银扫码"),
        JDPAY_H5("JDPAY_H5","京东H5支付"),
        ALIPAY_SCAN("ALIPAY_SCAN","支付宝扫码支付"),
        WXPAY_SCAN("WXPAY_SCAN","微信扫码支付"),
        WY("WY","网银支付"),
        IELPM_APP("IELPM_APP","易势移动支付"),
        IELPM_GATEWAY("IELPM_GATEWAY","易势网关支付"),
        ;

        ProductCode(String code, String name) {
            this.code = code;
            this.name = name;
        }

        private String code;
        private String name;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public static String getProductName(String code){
            for (ProductCode productCode : ProductCode.values()) {
                if(productCode.code.equalsIgnoreCase(code)){
                    return productCode.name;
                }
            }
            return "";
        }
    }

    /**
     * 产品类型
     */
    enum ProductType{
        PLATFORM("01","网关支付"),
        WY("02","网银支付"),
        KJ("03","快捷支付"),
        DF("04","代付"),
        SCAN("05","扫码支付"),
        DK("06","代扣"),
        QQ_SCAN("07","QQ扫码"),
        ALIPAY_SCAN("08","支付宝扫码"),
        WXPAY_SCAN("09","微信扫码"),
        BANK_SCAN("10","网银扫码"),
        WXPAY("11","微信支付"),
        ALIPAY("12","支付宝支付"),
        ;

        ProductType(String code, String name) {
            this.code = code;
            this.name = name;
        }

        private String code;
        private String name;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public static String getProductTypeName(String code){
            for (ProductType productType : ProductType.values()) {
                if(productType.code.equalsIgnoreCase(code)){
                    return productType.name;
                }
            }
            return "";
        }
    }

    /**
     * 账户交易类型
     */
    enum AccTranType{
        BANK_DEPOSIT("1002","银行存款"),
        REC_ACC("1122","应收账款"),
        PAY_ACC("2202","应付账款"),
        FEE_INCOME("6000","手续费收入"),
        FEE_OUTCOME("6421","手续费支出"),
        PRESTORE_ACC("2205","预存账款"),
        ;

        AccTranType(String code, String name) {
            this.code = code;
            this.name = name;
        }

        private String code;
        private String name;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public static String getAccTranTypeName(String code){
            for (AccTranType accTranType : AccTranType.values()) {
                if(accTranType.code.equalsIgnoreCase(code)){
                    return accTranType.name;
                }
            }
            return "";
        }
    }

    /**
     * 账户类型
     */
    enum AccType{
        MER_CASH_ACC("251","商户现金账户"),
        MER_TEMTRADE_ACC("252","商户交易临时账户"),
        MER_WAIT_SETTLE_ACC("253","商户待结算帐户"),
        MER_SETTLE_ACC("254","商户结算账户"),
        MER_FEE_ACC("255","商户手续费账户"),

        FUND_DEPOSIT_ACC("301","资金通道暂存款账户"),
        FUND_FEE_ACC("302","资金通道手续费账户"),
        ;

        AccType(String code, String name){
            this.code = code;
            this.name = name;
        }
        private String code;
        private String name;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public static String getAccTypeName(String code){
            for (AccType accType : AccType.values()) {
                if(accType.code.equalsIgnoreCase(code)){
                    return accType.name;
                }
            }
            return "";
        }
    }

    /**
     * 路由算法
     */
    interface Algorithm{
        /**权重比较*/
        public static final String WEIGHT_COMPARE = "00";
        /**逻辑过滤*/
        public static final String LOGIC_FILTER = "01";
    }

    interface FrzType{
        //冻结
        public static final int frz = 0;
        //解冻
        public static final int unfrz = 1;
    }

    /**
     * 代付类型
     */
    interface IssueBuniessType{
        /**
         * 提现
         */
        public static final String WITHDRAW = "1";
        /**
         * 结算
         */
        public static final String SETTLE = "2";
    }

    /**
     * 手续费类型
     */
    interface FeeChargeWay{

        /**
         * 固定费率
         */
        public static final String PERCENT = "01";

        /**
         * 固定手续费
         */
        public static final String FIXED = "00";

        /**
         * 阶梯费率
         */
        public static final String INTERVAL = "02";

    }

    /**
     * 手续费收取方式
     */
    interface FeeType{
        /**
         * 内收
         */
        public static final String INNER_FEE = "00";
        /**
         * 外扣
         */
        public static final String OUTER_FEE = "01";
    }

}
