package com.seektop.fund.payment.xinhuipay.common;


import com.seektop.fund.payment.xinhuipay.CommHead;

public class MsgRequest3510 {

	private CommHead comm_head = new CommHead();

	private MainData main_data = new MainData();

	public CommHead getComm_head() {
		return comm_head;
	}

	public MainData getMain_data() {
		return main_data;
	}

	/**
	 * @Description <p>
	 *              内部类实现报文体
	 *              </p>
	 * @author ty
	 * @since 2016年11月25日
	 * @version 1.0.0
	 * @ModifyByoo
	 *
	 */
	public static class MainData {
		
		private String signature="";
		
		private String payProducts="";
		
		private String txnType="";
		
		private String merId="";
		
		private String merName="";
		
		private String merAbbr="";
		
		private String orderId="";
		
		private String txnTime="";
		
		private String txnAmt="";
		
		private String currencyCode="";
		
		private String commodityName="";
		
		private String commoditDesc="";
		
		private String frontUrl="";
		
		private String backUrl="";
		
		private String payTimeout="";
		
		private String reserved="";
		
		private String prodExtend="";
		
		private String typeExtend="";
		
		public String getSignature() {
			return this.signature;
		}

		public MainData setSignature(String signature) {
			this.signature = signature;
			return this;
		}

		public String getPayProducts() {
			return this.payProducts;
		}

		public MainData setPayProducts(String payProducts) {
			this.payProducts = payProducts;
			return this;
		}

		public String getTxnType() {
			return this.txnType;
		}

		public MainData setTxnType(String txnType) {
			this.txnType = txnType;
			return this;
		}

		public String getMerId() {
			return this.merId;
		}

		public MainData setMerId(String merId) {
			this.merId = merId;
			return this;
		}

		public String getMerName() {
			return this.merName;
		}

		public MainData setMerName(String merName) {
			this.merName = merName;
			return this;
		}

		public String getMerAbbr() {
			return this.merAbbr;
		}

		public MainData setMerAbbr(String merAbbr) {
			this.merAbbr = merAbbr;
			return this;
		}

		public String getOrderId() {
			return this.orderId;
		}

		public MainData setOrderId(String orderId) {
			this.orderId = orderId;
			return this;
		}

		public String getTxnTime() {
			return this.txnTime;
		}

		public MainData setTxnTime(String txnTime) {
			this.txnTime = txnTime;
			return this;
		}

		public String getTxnAmt() {
			return this.txnAmt;
		}

		public MainData setTxnAmt(String txnAmt) {
			this.txnAmt = txnAmt;
			return this;
		}

		public String getCurrencyCode() {
			return this.currencyCode;
		}

		public MainData setCurrencyCode(String currencyCode) {
			this.currencyCode = currencyCode;
			return this;
		}

		public String getCommodityName() {
			return this.commodityName;
		}

		public MainData setCommodityName(String commodityName) {
			this.commodityName = commodityName;
			return this;
		}

		public String getCommoditDesc() {
			return this.commoditDesc;
		}

		public MainData setCommoditDesc(String commoditDesc) {
			this.commoditDesc = commoditDesc;
			return this;
		}

		public String getFrontUrl() {
			return this.frontUrl;
		}

		public MainData setFrontUrl(String frontUrl) {
			this.frontUrl = frontUrl;
			return this;
		}

		public String getBackUrl() {
			return this.backUrl;
		}

		public MainData setBackUrl(String backUrl) {
			this.backUrl = backUrl;
			return this;
		}

		public String getPayTimeout() {
			return this.payTimeout;
		}

		public MainData setPayTimeout(String payTimeout) {
			this.payTimeout = payTimeout;
			return this;
		}

		public String getReserved() {
			return this.reserved;
		}

		public MainData setReserved(String reserved) {
			this.reserved = reserved;
			return this;
		}

		public String getProdExtend() {
			return this.prodExtend;
		}

		public MainData setProdExtend(String prodExtend) {
			this.prodExtend = prodExtend;
			return this;
		}

		public String getTypeExtend() {
			return this.typeExtend;
		}

		public MainData setTypeExtend(String typeExtend) {
			this.typeExtend = typeExtend;
			return this;
		}

		@Override
		public String toString() {
			return "MainData [signature=" + signature + ", payProducts="
					+ payProducts + ", txnType=" + txnType + ", merId=" + merId
					+ ", merName=" + merName + ", merAbbr=" + merAbbr
					+ ", orderId=" + orderId + ", txnTime=" + txnTime
					+ ", txnAmt=" + txnAmt + ", currencyCode=" + currencyCode
					+ ", commodityName=" + commodityName + ", commoditDesc="
					+ commoditDesc + ", frontUrl=" + frontUrl + ", backUrl="
					+ backUrl + ", payTimeout=" + payTimeout + ", reserved="
					+ reserved + ", prodExtend=" + prodExtend + ", typeExtend="
					+ typeExtend + "]";
		}
		
	}

}