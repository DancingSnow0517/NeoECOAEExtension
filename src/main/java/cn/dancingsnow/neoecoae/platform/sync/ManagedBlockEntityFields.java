package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.ManagedFieldUtils;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/** Shares metadata per concrete class while retaining fields and RPC methods declared by its parents. */
final class ManagedBlockEntityFields {
    private static final ClassValue<ManagedFieldHolder> HOLDERS = new ClassValue<>() {
        @Override
        protected ManagedFieldHolder computeValue(Class<?> type) {
            Class<? extends IManaged> managedType = type.asSubclass(IManaged.class);
            ManagedFieldHolder holder = new ManagedFieldHolder(managedType);
            // LDLib 1.0.40.b creates field metadata without populating its RPC method table.
            holder.getRpcMethodMap().putAll(ManagedFieldUtils.getRPCMethods(managedType));
            Class<?> parent = type.getSuperclass();
            if (parent != null && IManaged.class.isAssignableFrom(parent)) {
                holder.merge(get(parent));
            }
            return holder;
        }
    };

    private ManagedBlockEntityFields() {}

    static ManagedFieldHolder get(Class<?> type) {
        return HOLDERS.get(type);
    }
}
