Ever since the reactive paradigm arrived on the scene, it immediately started to gain popularity and become one of the most favoured choice for building distributed and highly concurrent cloud-native backends. The performance, scalability and reliability benefits with reactive has inspired the community to do more innovations in this area and those concerted efforts are emerging in the form of  new specifications , drivers and protocols to further support reactive adoption across stacks and layers.

One of the reason for popularity of reactive is because it allows to program at higher level of abstraction by using reusable operators to form composable pipelines.  

Threads and schedulers that run the pipeline are used efficiently  behind the scenes  as enablers and abstracted away from developers.

However, in order to develop a deeper understanding of reactive it's very important to un-peel the abstractions and unravel  how composability works under the hood.

This post focuses on explaining reactive stream specifications and the pattern that is used to  form composable pipelines. The pattern described here is also relevant to  understand how  [ProjectReactor](https://projectreactor.io/)  implements and provides reusable operators. 

## Reactive Streams
In order to arrive at standardization on reactive paradigm for different languages,  [reactive streams specifications](https://www.reactive-streams.org/)  was formulated.

The specification consists of following major APIs which perhaps is a  fusion of observer and iterator design pattern. The reactive streams specifications  allow for push-pull model with back-pressure control;  a concept that was hitherto missing in webserver and webclient libraries. 

#### Publisher
 [Publisher](https://www.reactive-streams.org/reactive-streams-1.0.2-javadoc/org/reactivestreams/Publisher.html) , as the name suggests, is the source of the stream of events that flow to the subscriber(s) 
```
public interface Publisher<T> {

    public void subscribe(Subscriber<? super T> s);
}
```
#### Subscriber
[Subscriber](https://www.reactive-streams.org/reactive-streams-1.0.2-javadoc/org/reactivestreams/Subscriber.html) is the entity in the interaction which subscribes to publisher, makes the demand and then receive the stream of the events through `onNext`.

Completion of stream is signaled by `onComplete` and error is reported by onError. 
```
public interface Subscriber<T> {

    public void onSubscribe(Subscription s);

    public void onNext(T t);

    public void onError(Throwable t);

    public void onComplete();
}
```
#### Subscription
[Subscription](https://www.reactive-streams.org/reactive-streams-1.0.2-javadoc/org/reactivestreams/Subscription.html) is sort of handle given by publisher to subscriber through which subscriber 
can control the rate of data emitted to it and cancel/terminate the demand. 
```
public interface Subscription {
   
    public void request(long n);

    public void cancel();
}
```

The richness of reactive is in its range of out-of-the-box operators which are used to form  composable pipelines. The operators  are essentially various implementations of publisher and subscriber interfaces - explained further.

### Reactive Lifecycle
The reactive pipeline is composition of various operators and the pipeline has two phases  -  assembly and subscription.  


#### **Assembly phase:** 
In  this phase the operators are chained together (think LinkedList ).Each operator takes a source publisher as input, wraps it and passes itself to the next operator in the chain. 

![image.png](https://cdn.hashnode.com/res/hashnode/image/upload/v1619953082658/AXHmDDTbD.png)

Creating a fluent pattern for reactive pipeline is not a must-have but it gives it DSL kind of readability and makes it more intuitive and intention revealing.

In order to create  fluent pattern, we will put DSL constructs inside a class. In  [ProjectReactor](https://projectreactor.io/) , this ask is accomplished by   [Mono](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html)  and [Flux](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html) - they both contain the DSL constructs for various operators.

Lets implement a simple  Mono to understand the principle
```
public  abstract class MyMono<T> implements Publisher<T> {

    static  <T> MyMono<T> from(T[] a){
        return new ArrayPublisher(a);
    }

    <R> MyMono<T> map(Function<T,R> function){
        return new MapPublisher<T, R>(this,function);
    }

    MyMono<T> filter(Predicate<T> function){
        return new FilterPublisher<T>(this,function);
    }
}

```
With this simplest Mono, we can compose a pipeline like 
 
```
MyMono.from(IntStream.range(1,10).boxed().toArray(Integer[]::new))
 .map(i -> i *2)
 .filter(x -> x%4==0)
 .subscribe(System.out::println)
```

- `map`  is resolved to a `MapPublisher` which wraps the source `ArrayPublisher`. 
- `filter`  wraps `MapPublisher` and returns `FilterPublisher`
- finally, subscribe is actually called on `FilterPublisher`

The above is the assembly phase. It is only weaving the chain of publisher but, the logic (i.e. lambda's inside each operator) is weaved and executed the subscription happens.

We will see this in next section.  

#### Subscription phase
Subscription phase is where the execution happens, like  in java streams where nothing happens till we add a terminal operation, similarly, in reactive pipeline nothing happens until a subscriber arrives.

From analogy standpoint, we can think of assembly phase as setting up a conveyer belt and subscription phase is when an item is put on that conveyer belt. 

In the pipeline defined above, subscription happens immediately because we have called subscribe with an executable instruction ( i.e `System.out::println`) immediately without any delay. 

However, if we think of web server scenario then subscription phase begins when a request is made to the server and socket is opened. Reactor netty is an example of this and we will see it in detail in our next blog

For, now lets understand how business logic gets weaved in during subscription phase. We will take the sample pipeline defined above

```
        MyMono.<Integer>from(IntStream.range(1,10).boxed().toArray(Integer[]::new))
                .map(i-> i*2)
                .filter(i -> i%4==0)
                .subscribe(new PrintSubscriber());
```
For this chain  following diagram illustrates the sequence of actions
![image.png](https://cdn.hashnode.com/res/hashnode/image/upload/v1619951481349/f-AUAbxDm.png)


 * The publisher at the root of the operator chain (in this case `ArrayPublisher`) is the one that is responsible to emit/publish the data down the operator chain. It is also the source of subscription instance which is created and passed to subscriber.

```
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
```
 * The intermediate operators (like `map`, `filter` etc) use decorator pattern to augment the actual subscriber with specific business logic pertaining to that operator. 

```
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
```
#### Everything starts when you subscribe 
**Step 1 - Chained Subscribes**
* `PrintSubscriber` subscribes to `FilterPublisher`
* `FilterPublisher` creates `FilterSubscriber` and with that subscribes to `MapPublisher`
* `MapPublisher` creates `MapSubscriber` and with that subscribes to `ArrayPublisher`

**Step 2 - Receive Subscription**
* `ArrayPublisher` creates and passes subscription to `MapSubscriber`
* `MapSubscriber` propagates subscription to `FilterSubscriber`
* `FilterSubscriber` propagates subscription to `PrintSubscriber`

**Step 3 - Request Data**
* `PrintSubscriber` requests data from `ArrayPublisher` by calling `request(1)` on `subscription`

**Step 4 - OnNext**
* `ArrayPublisher` emit an element and calls `onNext` on `MapSubscriber`
* `MapSubscriber` applies mapper function on the element and calls `onNext` on `FilterSubscriber`
* `FilterSubscriber` uses predicate on received element and if `true` the calls `onNext` on `PrintSubscriber` . If `false` then requests next element by calling request() on subscription
* `PrintSubscriber` prints the element and calls `request()` on subscription to request `ArrayPublisher` for next element

**Step 4 - OnComplete**
* When all the elements are emitted from `ArrayPublisher` then it calls `onComplete` on the subscriber
* `onComplete` is then propagated to all subsequent subscribers 


In the next post, we will see an example of reactive webserver using netty and see how  what happens under the hood when client makes a request to the server. How request establishes the subscription  and   how server handles and responds  to the request in reactive parlance.   