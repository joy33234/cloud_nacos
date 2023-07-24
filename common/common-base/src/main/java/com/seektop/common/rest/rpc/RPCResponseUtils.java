package com.seektop.common.rest.rpc;

import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import org.apache.commons.lang3.StringUtils;

public class RPCResponseUtils {

	/**
	 * 成功调用
	 *
	 * @param rpcResponse
	 * @return
	 */
	public static boolean isSuccess(RPCResponse<?> rpcResponse) {
		return rpcResponse != null && rpcResponse.getCode() == RPCResponseCode.SUCCESS.getCode();
	}

	/**
	 * 失败调用
	 *
	 * @param rpcResponse
	 * @return
	 */
	public static boolean isFail(RPCResponse<?> rpcResponse) {
		return rpcResponse == null || rpcResponse.getCode() != RPCResponseCode.SUCCESS.getCode();
	}

	/**
	 * 如果调用失败， 抛出 gloabalException。如果调用成功，返回正确的数据
	 * @param rpcResponse
	 * @param <T>
	 * @return
	 * @throws GlobalException
	 */
	public static <T> T getData(RPCResponse<T> rpcResponse) throws GlobalException {
		if(isFail(rpcResponse)){
			if (StringUtils.isBlank(rpcResponse.getMessage())) {
				throw new GlobalException(ResultCode.SERVER_ERROR);
			}
			else {
				throw new GlobalException(rpcResponse.getMessage());
			}
		}
		return rpcResponse.getData();
	}

	/**
	 * 构建一个success 类型的 RPCResponse
	 * @param data
	 * @param <T>
	 * @return
	 */
	public static <T> RPCResponse<T> buildSuccessRpcResponse(T data){
		RPCResponse.Builder<T> newBuilder = RPCResponse.newBuilder();
		return newBuilder.success().setData(data).build();
	}
}