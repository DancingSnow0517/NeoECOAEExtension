package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc;

import java.util.function.*;
public final class RPCEventBuilder {
    @FunctionalInterface public interface TriFunction<A,B,C,R>{R apply(A a,B b,C c);}
    public record Event(Class<?>[] parameters,Class<?> result,Function<Object[],Object> handler){}
    private RPCEventBuilder(){}
    public static <A> Event simple(Class<A>a,Consumer<A> handler){return new Event(new Class<?>[]{a},null,v->{handler.accept(a.cast(v[0]));return null;});}
    public static <A,R> Event simple(Class<A>a,Class<R>r,java.util.function.Function<A,R> handler){return new Event(new Class<?>[]{a},r,v->handler.apply(a.cast(v[0])));}
    public static <A,B> Event simple(Class<A>a,Class<B>b,BiConsumer<A,B> handler){return new Event(new Class<?>[]{a,b},null,v->{handler.accept(a.cast(v[0]),b.cast(v[1]));return null;});}
    public static <A,B,R> Event simple(Class<A>a,Class<B>b,Class<R>r,BiFunction<A,B,R> handler){return new Event(new Class<?>[]{a,b},r,v->handler.apply(a.cast(v[0]),b.cast(v[1])));}
    public static <A,B,C,R> Event simple(Class<A>a,Class<B>b,Class<C>c,Class<R>r,TriFunction<A,B,C,R> handler){return new Event(new Class<?>[]{a,b,c},r,v->handler.apply(a.cast(v[0]),b.cast(v[1]),c.cast(v[2])));}
}
