package com.seektop.common.utils;

import java.util.function.Supplier;

public class TimerLocalCache <T>{

    private T val;
    private long expired;
    private Supplier<T> function;

    public TimerLocalCache(long second, Supplier<T> function){
        this(second,function,function.get());
    }
    public TimerLocalCache(long second, Supplier<T> function,T val){
        this.expired = System.currentTimeMillis()+(second*1000);
        this.function = function;
        this.val = val;
    }
    private TimerLocalCache(){
    }
    public T getVal(){
        if (expired<System.currentTimeMillis()) this.val = function.get();
        return val;
    }
}
