package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.IBindable;
import java.util.*;
import java.util.function.Consumer;
public class BindableValue<T> extends UIElement implements IBindable<T>{
    private T value;private final List<Consumer<T>> listeners=new ArrayList<>();
    public BindableValue(T value){this.value=value;}
    @Override public T getValue(){return value;}
    @Override public BindableValue<T> setValue(T value){return setValue(value,true);}
    public BindableValue<T> setValue(T value,boolean notify){boolean changed=!Objects.equals(this.value,value);this.value=value;if(changed&&notify)listeners.forEach(listener->listener.accept(value));return this;}
    public BindableValue<T> registerValueListener(Consumer<T> listener){listeners.add(listener);return this;}
}
