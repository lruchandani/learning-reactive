package com.reactive.learning.composability;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public class MyReactivePipeline {

    public static void main(String[] args) {
                MyFlux.<Integer>from(IntStream.range(1,10).boxed().toArray(Integer[]::new))
                .map(i-> i*2)
                .filter(i -> i%4==0)
                .subscribe(new PrintSubscriber());
    }

    public static class ArrayPublisher<T> extends MyFlux<T>{
        private T[] a;

        public ArrayPublisher(T[] a) {
            this.a = a;
        }

        @Override
        public void subscribe(Subscriber<? super T> s) {
            s.onSubscribe(new Subscription() {
                private AtomicInteger read = new AtomicInteger();
                @Override
                public void request(long n) {
                    if(read.get()==a.length){
                        s.onComplete();
                        return;
                    }

                    while(read.get()<=a.length-1 && n >0){
                     s.onNext(a[read.getAndIncrement()]);
                     n--;
                    }
                }

                @Override
                public void cancel() {
                    s.onComplete();
                }
            });
        }
    }

    public static class MapPublisher<T,R> extends MyFlux<T>{

        Publisher<T> source;
        Function<T,R> mapper;

        public MapPublisher(Publisher<T> source, Function<T, R> mapper) {
            this.source = source;
            this.mapper = mapper;
        }

        @Override
        public void subscribe(Subscriber<? super T> s) {
            source.subscribe(new MapSubscriber(s,mapper));
        }
    }

    public static class FilterPublisher<T> extends MyFlux<T> {

        Publisher<T> source;
        Predicate<T> predicate;

        public FilterPublisher(Publisher<T> source, Predicate<T> predicate) {
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public void subscribe(Subscriber<? super T> s) {
            source.subscribe(new FilterSubscriber<T>(s,predicate));
        }
    }

    public static class FilterSubscriber<T> implements Subscriber<T>{

        Subscriber<? super T> destination;
        Predicate<T> predicate;
        Subscription subscription;
        boolean done;

        public FilterSubscriber(Subscriber<? super T> destination, Predicate<T> predicate) {
            this.destination = destination;
            this.predicate = predicate;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
            destination.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            if(predicate.test(t)) {
                destination.onNext(t);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable t) {

        }

        @Override
        public void onComplete() {
            if(done){
                return;
            }
            done=true;
            System.out.println("Calling from Filter");
            destination.onComplete();
        }
    }

    public static class MapSubscriber<T,R> implements  Subscriber<T>{

        Subscriber<R> destination;
        Function<T,R> mapper;

        boolean done;

        public MapSubscriber(Subscriber<R> destination,Function<T,R> mapper) {
            this.destination = destination;
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Subscription s) {
            destination.onSubscribe(s);
        }

        @Override
        public void onNext(T integer) {
            destination.onNext(mapper.apply(integer));
        }

        @Override
        public void onError(Throwable t) {

        }

        @Override
        public void onComplete() {
            if(done){
                return;
            }
            done=true;
            System.out.println("Calling from Map");
            destination.onComplete();
        }
    }


    public static class PrintSubscriber implements Subscriber<Integer>{

        Subscription subscription ;
        boolean done;

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
            s.request(1);
        }

        @Override
        public void onNext(Integer integer) {
            System.out.println(integer);
            this.subscription.request(1);
        }

        @Override
        public void onError(Throwable t) {

        }

        @Override
        public void onComplete() {
            if(done){
                return;
            }
            done=true;
            System.out.println("Done");
        }
    }



}
