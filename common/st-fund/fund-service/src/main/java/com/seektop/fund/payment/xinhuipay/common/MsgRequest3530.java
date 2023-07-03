package com.seektop.fund.payment.xinhuipay.common;


import com.seektop.fund.payment.xinhuipay.CommHead;

public class MsgRequest3530 {

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
	 * @ModifyBy
	 *
	 */
	public static class MainData {
		
		private String signature;
		private String dfType;
		private String merId;
		private String merName;
		private String orderId;
		private String txnTime;
		private String txnAmt;
		private String currencyCode;
		private String bankNo;
		private String bankName;
		private String bankBranchName;
		private String bankProvince;
		private String bankCity;
		private String accType;
		private String accName;
		private String accNo;
		private String certType;
		private String certNo;
		private String phoneNo;
		private String backUrl;
		private String reserved;
		
		public String getSignature() {
			return signature;
		}

		public void setSignature(String signature) {
			this.signature = signature;
		}

		public String getDfType() {
			return dfType;
		}

		public void setDfType(String dfType) {
			this.dfType = dfType;
		}

		public String getMerId() {
			return merId;
		}

		public void setMerId(String merId) {
			this.merId = merId;
		}

		public String getMerName() {
			return merName;
		}

		public void setMerName(String merName) {
			this.merName = merName;
		}

		public String getOrderId() {
			return orderId;
		}

		public void setOrderId(String orderId) {
			this.orderId = orderId;
		}

		public String getTxnTime() {
			return txnTime;
		}

		public void setTxnTime(String txnTime) {
			this.txnTime = txnTime;
		}

		public String getTxnAmt() {
			return txnAmt;
		}

		public void setTxnAmt(String txnAmt) {
			this.txnAmt = txnAmt;
		}

		public String getCurrencyCode() {
			return currencyCode;
		}

		public void setCurrencyCode(String currencyCode) {
			this.currencyCode = currencyCode;
		}

		public String getBankNo() {
			return bankNo;
		}

		public void setBankNo(String bankNo) {
			this.bankNo = bankNo;
		}

		public String getBankName() {
			return bankName;
		}

		public void setBankName(String bankName) {
			this.bankName = bankName;
		}

		public String getBankBranchName() {
			return bankBranchName;
		}

		public void setBankBranchName(String bankBranchName) {
			this.bankBranchName = bankBranchName;
		}

		public String getBankProvince() {
			return bankProvince;
		}

		public void setBankProvince(String bankProvince) {
			this.bankProvince = bankProvince;
		}

		public String getBankCity() {
			return bankCity;
		}

		public void setBankCity(String bankCity) {
			this.bankCity = bankCity;
		}

		public String getAccType() {
			return accType;
		}

		public void setAccType(String accType) {
			this.accType = accType;
		}

		public String getAccName() {
			return accName;
		}

		public void setAccName(String accName) {
			this.accName = accName;
		}

		public String getAccNo() {
			return accNo;
		}

		public void setAccNo(String accNo) {
			this.accNo = accNo;
		}

		public String getCertType() {
			return certType;
		}

		public void setCertType(String certType) {
			this.certType = certType;
		}

		public String getCertNo() {
			return certNo;
		}

		public void setCertNo(String certNo) {
			this.certNo = certNo;
		}

		public String getPhoneNo() {
			return phoneNo;
		}

		public void setPhoneNo(String phoneNo) {
			this.phoneNo = phoneNo;
		}

		public String getBackUrl() {
			return backUrl;
		}

		public void setBackUrl(String backUrl) {
			this.backUrl = backUrl;
		}

		public String getReserved() {
			return reserved;
		}

		public void setReserved(String reserved) {
			this.reserved = reserved;
		}
		
	}

}