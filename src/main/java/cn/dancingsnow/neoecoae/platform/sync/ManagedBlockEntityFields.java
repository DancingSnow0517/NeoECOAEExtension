package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/** Shares metadata per concrete class while retaining fields and RPC methods declared by its parents. */
final class ManagedBlockEntityFields {
    private static final ClassValue<ManagedFieldHolder> HOLDERS = new ClassValue<>() {
        @Override
        protected ManagedFieldHolder computeValue(Class<?> type) {
            Class<? extends IManaged> managedType = type.asSubclass(IManaged.class);
            Class<?> parent = type.getSuperclass();
            if (parent != null && IManaged.class.isAssignableFrom(parent)) {
                return new ManagedFieldHolder(managedType, get(parent));
            }
            return new ManagedFieldHolder(managedType);
        }
    };

    private ManagedBlockEntityFields() {}

    static ManagedFieldHolder get(Class<?> type) {
        return HOLDERS.get(type);
    }
}
