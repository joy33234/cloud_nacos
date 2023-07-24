package com.seektop.fund.payment.xinhuipay.common;


import com.seektop.fund.payment.xinhuipay.CommHead;

public class MsgRequest3903 {

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
		
		private String signature="";
		private String merId="";
		private String orderId="";
		private String txnTime="";
		private String txnAmt="";
		private String orderQid="";
		private String reqReserved="";
		private String reserved="";
		public String getSignature() {
			return signature;
		}
		public MainData setSignature(String signature) {
			this.signature = signature;
			return this;
		}
		public String getMerId() {
			return merId;
		}
		public MainData setMerId(String merId) {
			this.merId = merId;
			return this;
		}
		public String getOrderId() {
			return orderId;
		}
		public MainData setOrderId(String orderId) {
			this.orderId = orderId;
			return this;
		}
		public String getTxnTime() {
			return txnTime;
		}
		public MainData setTxnTime(String txnTime) {
			this.txnTime = txnTime;
			return this;
		}
		public String getTxnAmt() {
			return txnAmt;
		}
		public MainData setTxnAmt(String txnAmt) {
			this.txnAmt = txnAmt;
			return this;
		}
		public String getOrderQid() {
			return orderQid;
		}
		public MainData setOrderQid(String orderQid) {
			this.orderQid = orderQid;
			return this;
		}
		public String getReqReserved() {
			return reqReserved;
		}
		public MainData setReqReserved(String reqReserved) {
			this.reqReserved = reqReserved;
			return this;
		}
		public String getReserved() {
			return reserved;
		}
		public MainData setReserved(String reserved) {
			this.reserved = reserved;
			return this;
		}
		@Override
		public String toString() {
			return "MainData [signature=" + signature + ", merId=" + merId
					+ ", orderId=" + orderId + ", txnTime=" + txnTime
					+ ", txnAmt=" + txnAmt + ", orderQid=" + orderQid
					+ ", reqReserved=" + reqReserved + ", reserved=" + reserved
					+ "]";
		}		
		
	}

}