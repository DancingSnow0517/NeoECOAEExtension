package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
public interface IBindable<T> extends IDataSource<T> {
    @SuppressWarnings({"unchecked","rawtypes"}) default void bind(DataBindingBuilder.Binding<? extends T> binding) {
        if (!(this instanceof UIElement element)) throw new IllegalStateException("Bindings require a menu-owned UI element");
        element.attachBinding(this,(DataBindingBuilder.Binding)binding);
    }
}
