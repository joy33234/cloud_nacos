package com.seektop.common.rest.rpc;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RPCResponse<T> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2054492155488251365L;

	/**
	 * 响应的消息内容
	 */
	private String message;

	/**
	 * 响应的状态码
	 */
	private Integer code;

	/**
	 * 响应的数据内容
	 */
	private T data;

	public RPCResponse(Builder<T> builder) {
		this.message = builder.message;
		this.code = builder.code;
		this.data = builder.data;
	}

	/**
	 * RPCResponse 并非一个复用对象, 使用建造者模式没有获得显著提升
	 * 由于Builder与RPCResponse为不同对象, 导致在链式调用中无法传递范型类型
	 */
	public static <T> Builder<T> newBuilder() {
		return new Builder<T>();
	}

	public static class Builder<T> {

		private String message;
		private Integer code;
		private T data;

		private Builder() {
		}

		public Builder<T> success() {
			this.code = RPCResponseCode.SUCCESS.getCode();
			return this;
		}

		public Builder<T> success(final Integer code) {
			this.code = code;
			return this;
		}

		public Builder<T> fail() {
			this.code = RPCResponseCode.FAIL_DEFAULT.getCode();
			return this;
		}

		public Builder<T> fail(final Integer code) {
			this.code = code;
			return this;
		}

		public Builder<T> fail(RPCResponseCode code) {
			this.code = code.getCode();
			this.message = code.getMessage();
			return this;
		}

		public Builder<T> paramError() {
			this.code = RPCResponseCode.PARAM_ERROR.getCode();
			this.message = RPCResponseCode.PARAM_ERROR.getMessage();
			return this;
		}

		public Builder<T> serverError() {
			this.code = RPCResponseCode.SERVER_ERROR.getCode();
			this.message = RPCResponseCode.SERVER_ERROR.getMessage();
			return this;
		}

		public Builder<T> setMessage(String message) {
			this.message = message;
			return this;
		}

		public Builder<T> setCode(Integer code) {
			this.code = code;
			return this;
		}

		public Builder<T> setData(T t) {
			this.data = t;
			return this;
		}

		public RPCResponse<T> build() {
			return new RPCResponse<T>(this);
		}

	}

}