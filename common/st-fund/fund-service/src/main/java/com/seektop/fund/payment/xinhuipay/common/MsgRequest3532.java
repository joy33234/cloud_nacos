package com.seektop.fund.payment.xinhuipay.common;


import com.seektop.fund.payment.xinhuipay.CommHead;

public class MsgRequest3532 {

	private CommHead comm_head = new CommHead();

	private MainData main_data = new MainData();

	public CommHead getComm_head() {
		return comm_head;
	}

	public MainData getMain_data() {
		return main_data;
	}

	/**
	 * @Description
	 * <p>
	 * 内部类实现报文体
	 * </p>
	 * @author ty
	 * @since 2016年11月25日
	 * @version 1.0.0
	 * @ModifyBy
	 *
	 */
	public static class MainData {

		private String signature;
		private String merId;
		private String queryTime;

		public String getSignature() {
			return signature;
		}

		public void setSignature(String signature) {
			this.signature = signature;
		}

		public String getMerId() {
			return merId;
		}

		public void setMerId(String merId) {
			this.merId = merId;
		}

		public String getQueryTime() {
			return queryTime;
		}

		public void setQueryTime(String queryTime) {
			this.queryTime = queryTime;
		}
	}
}