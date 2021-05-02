package com.reactive.learning.composability;

import org.reactivestreams.Publisher;

import java.util.function.Function;
import java.util.function.Predicate;

public  abstract class MyFlux<T> implements Publisher<T> {

    static  <T> MyFlux<T> from(T[] a){
        return new MyReactivePipeline.ArrayPublisher(a);
    }

      <R> MyFlux<T> map(Function<T,R> function){
        return new MyReactivePipeline.MapPublisher<T, R>(this,function);
    }

    MyFlux<T> filter(Predicate<T> function){
        return new MyReactivePipeline.FilterPublisher<T>(this,function);
    }
}
