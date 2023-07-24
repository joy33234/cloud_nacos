package com.seektop.common.elasticsearch.base;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class KVResult<K,V> implements Serializable {

	private K key;

	private V val;

}