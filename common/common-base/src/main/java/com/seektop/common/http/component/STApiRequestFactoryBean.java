package com.seektop.common.http.component;

import lombok.Data;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.cglib.proxy.Enhancer;

import java.util.Objects;

@Data
public class STApiRequestFactoryBean<T>  implements FactoryBean<T> {

	   private RequestMethodInvocationHandler requestMethodInvocationHandler;

	   private Class<T> interfaceClass;

	   private String value;
	   
	    /**
	     * 新建bean
	     * @return
	     * @throws Exception
	     */
	    @Override
	    public T getObject() throws Exception {
	    	 // 检查 h 不为空，否则抛异常
	        Objects.requireNonNull(interfaceClass);
	        
	        return (T) Enhancer.create(interfaceClass,requestMethodInvocationHandler);
	    }

	    /**
	     * 获取bean
	     * @return
	     */
	    @Override
	    public Class<?> getObjectType() {
	        return interfaceClass;
	    }

	    @Override
	    public boolean isSingleton() {
	        return true;
	    }
}