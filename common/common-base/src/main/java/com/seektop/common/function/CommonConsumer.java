package com.seektop.common.function;

@FunctionalInterface
public interface CommonConsumer<T> {
    void execute(T param);
}
