package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings;
public interface IDataSource<T> {
    T getValue();
    IDataSource<T> setValue(T value);
}
