package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.SyncStrategy;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public final class DataBindingBuilder<T> {
    public record Binding<T>(Supplier<T> source,Consumer<T> sink,Class<?> type,SyncStrategy s2c,SyncStrategy c2s,T initial,boolean hasInitial) {}
    private T initial;private boolean hasInitial;
    private final Supplier<T> source;
    private final Consumer<T> sink;
    private Class<?> type;
    private SyncStrategy s2c=SyncStrategy.CHANGES,c2s=SyncStrategy.CHANGES;
    private DataBindingBuilder(Supplier<T> source,Consumer<T> sink,Class<?> type) {this.source=source;this.sink=sink;this.type=type;}
    public static <T> DataBindingBuilder<T> create(Supplier<T> source,Consumer<T> sink) {return new DataBindingBuilder<>(source,sink,null);}
    public static DataBindingBuilder<Integer> intValS2C(Supplier<Integer> source) {return s2c(source,Integer.class);}
    public static DataBindingBuilder<Integer> intValC2S(Consumer<Integer> sink) {return c2s(sink,Integer.class);}
    public static DataBindingBuilder<Boolean> boolS2C(Supplier<Boolean> source) {return s2c(source,Boolean.class);}
    public static DataBindingBuilder<Float> floatValS2C(Supplier<Float> source) {return s2c(source,Float.class);}
    public static DataBindingBuilder<Component> componentS2C(Supplier<Component> source) {return s2c(source,Component.class);}
    public static DataBindingBuilder<String> stringS2C(Supplier<String> source) {return s2c(source,String.class);}
    public static <T extends Tag> DataBindingBuilder<T> tagS2C(Supplier<T> source) {var result=new DataBindingBuilder<T>(source,null,Tag.class);result.c2s=SyncStrategy.NONE;return result;}
    public static DataBindingBuilder<String> string(Supplier<String> source,Consumer<String> sink) {return new DataBindingBuilder<>(source,sink,String.class);}
    private static <T> DataBindingBuilder<T> s2c(Supplier<T> source,Class<T> type) {
        var result=new DataBindingBuilder<T>(source,null,type);result.c2s=SyncStrategy.NONE;return result;
    }
    private static <T> DataBindingBuilder<T> c2s(Consumer<T> sink,Class<T> type) {
        var result=new DataBindingBuilder<T>(null,sink,type);result.s2c=SyncStrategy.NONE;return result;
    }
    public DataBindingBuilder<T> syncType(Class<?> type) {this.type=type;return this;}
    public DataBindingBuilder<T> c2sStrategy(SyncStrategy strategy) {c2s=strategy;return this;}
    public DataBindingBuilder<T> s2cStrategy(SyncStrategy strategy) {s2c=strategy;return this;}
    public DataBindingBuilder<T> initialValue(T value){initial=value;hasInitial=true;return this;}
    public Binding<T> build() {if(type==null)throw new IllegalStateException("Missing binding wire type");return new Binding<>(source,sink,type,s2c,c2s,initial,hasInitial);}
}
