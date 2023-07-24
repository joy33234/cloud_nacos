package com.seektop.fund.payment.xinhuipay;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class CommHead {
	/**
	 * 版本号
	 */
	private String verNo = "001";
	
	/**
	 * 发起方渠道代码
	 */
	private String sndChnlNo = "";

	/**
	 * 接收方渠道代码
	 */
	private String rcvChnlNo = "00";
	
	/**
	 * 交易码
	 */
	protected String txnNo = "";
	
	/**
	 * 渠道日期
	 */
	private String chnlDt = new SimpleDateFormat("yyyyMMdd").format(new Date());
	
	/**
	 * 渠道时间
	 */
	private String chnlTm = new SimpleDateFormat("HHmmss").format(new Date());
	
	/**
	 * 系统日期
	 */
	private String hostDt = "";
	
	/**
	 * 系统时间
	 */
	private String hostTm = "";
	
	/**
	 * 发起方流水号
	 */
	private String chnlSeq = System.currentTimeMillis()/1000+((new Random().nextInt(80)+10) + "");
	
	/**
	 * 支付系统流水号
	 */
	private String hostSeq = "";
	
	/**
	 * 返回码
	 */
	private String rspNo = "";
	
	/**
	 * 返回信息
	 */
	private String rspMsg = "";

	public String getVerNo() {
		return verNo;
	}

	public CommHead setVerNo(String verNo) {
		this.verNo = verNo;
		return this;
	}

	public String getSndChnlNo() {
		return sndChnlNo;
	}

	public CommHead setSndChnlNo(String sndChnlNo) {
		this.sndChnlNo = sndChnlNo;
		return this;
	}

	public String getRcvChnlNo() {
		return rcvChnlNo;
	}

	public CommHead setRcvChnlNo(String rcvChnlNo) {
		this.rcvChnlNo = rcvChnlNo;
		return this;
	}

	public String getTxnNo() {
		return txnNo;
	}

	public CommHead setTxnNo(String txnNo) {
		this.txnNo = txnNo;
		return this;
	}

	public String getChnlDt() {
		return chnlDt;
	}

	public CommHead setChnlDt(String chnlDt) {
		this.chnlDt = chnlDt;
		return this;
	}

	public String getChnlTm() {
		return chnlTm;
	}

	public CommHead setChnlTm(String chnlTm) {
		this.chnlTm = chnlTm;
		return this;
	}

	public String getHostDt() {
		return hostDt;
	}

	public CommHead setHostDt(String hostDt) {
		this.hostDt = hostDt;
		return this;
	}

	public String getHostTm() {
		return hostTm;
	}

	public CommHead setHostTm(String hostTm) {
		this.hostTm = hostTm;
		return this;
	}

	public String getChnlSeq() {
		return chnlSeq;
	}

	public CommHead setChnlSeq(String chnlSeq) {
		this.chnlSeq = chnlSeq;
		return this;
	}

	public String getHostSeq() {
		return hostSeq;
	}

	public CommHead setHostSeq(String hostSeq) {
		this.hostSeq = hostSeq;
		return this;
	}

	public String getRspNo() {
		return rspNo;
	}

	public CommHead setRspNo(String rspNo) {
		this.rspNo = rspNo;
		return this;
	}

	public String getRspMsg() {
		return rspMsg;
	}

	public CommHead setRspMsg(String rspMsg) {
		this.rspMsg = rspMsg;
		return this;
	}

	@Override
	public String toString() {
		return "Comm_head [verNo=" + verNo + ", sndChnlNo=" + sndChnlNo
				+ ", rcvChnlNo=" + rcvChnlNo + ", txnNo=" + txnNo + ", chnlDt="
				+ chnlDt + ", chnlTm=" + chnlTm + ", hostDt=" + hostDt
				+ ", hostTm=" + hostTm + ", chnlSeq=" + chnlSeq + ", hostSeq="
				+ hostSeq + ", rspNo=" + rspNo + ", rspMsg=" + rspMsg + "]";
	}
	
}
