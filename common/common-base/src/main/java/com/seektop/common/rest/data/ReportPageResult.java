package com.seektop.common.rest.data;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class ReportPageResult<T> implements Serializable {

	private long total;

	private String cursor = "0";
	
	private int pageNum;
	
	private int pageSize;

	private List<T> list = new ArrayList<>();

	/**
	 * 汇总
	 */
	private T sum;
	/**
	 * 汇总(分币种汇总)
	 */
	private List<T> sumList;

	public ReportPageResult() {
	}

	public ReportPageResult(T sum) {
		this.sum = sum;
	}

	public ReportPageResult(List<T> sumList) {
		this.sumList = sumList;
	}

	public void subList(int offset, int size) {
		list = subList(list,offset,size);
	}
	public static <R> List<R> subList(List<R> list,int offset, int size) {
		if (list!=null&&list.size()>0){
			int fromIndex = Integer.max(0, Integer.min(offset, list.size()));
			int toIndex = Integer.min(offset + size, list.size());
			return list.subList(fromIndex, toIndex);
		}
		return Collections.EMPTY_LIST;
	}

}