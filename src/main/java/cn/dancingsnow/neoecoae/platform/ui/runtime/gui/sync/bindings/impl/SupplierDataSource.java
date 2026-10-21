package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.IDataSource;
import java.util.function.Supplier;
public record SupplierDataSource<T>(Supplier<T> supplier) implements IDataSource<T> {
    public static <T> SupplierDataSource<T> of(Supplier<T> supplier) {return new SupplierDataSource<>(supplier);}
    @Override public T getValue(){return supplier.get();}
    @Override public IDataSource<T> setValue(T value){throw new UnsupportedOperationException("Read-only local data source");}
}
