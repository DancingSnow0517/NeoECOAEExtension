package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEKey;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.MatterType;
import com.buuz135.replication.api.matter_fluid.MatterStack;
import dev.lapis256.apprep.api.ae2.stack.MatterKey;

/** ECO-only entry to the upstream Matter factories, retaining unknown-type and empty-stack handling. */
public final class ECOMatterKeys {
    private ECOMatterKeys() {}
    private static final boolean SHAREABLE = SharedMatterKey.class.isAssignableFrom(MatterKey.class);
    public static MatterKey of(IMatterType type) {
        Object epoch = KeyReuse.epoch();
        if (epoch == null || !SHAREABLE || !(type instanceof MatterType)) return MatterKey.Companion.of(type);
        KeyReuseSlot slot = KeyReuse.slot(epoch, MatterKey.class, type);
        if (slot == null) return MatterKey.Companion.of(type);
        MatterKey key = (MatterKey) slot.get(epoch);
        if (key != null) return key;
        synchronized (slot) {
            key = (MatterKey) slot.get(epoch);
            if (key == null) {
                key = MatterKey.Companion.of(type);
                if ((Object) key instanceof SharedMatterKey shared) {
                    shared.neoecoae$markShared();
                    slot.set(epoch, key);
                }
            }
            return key;
        }
    }
    public static MatterKey of(MatterStack stack) {
        if (stack == null || stack.isEmpty()) return MatterKey.Companion.of(stack);
        return of(stack.getMatterType());
    }
    static AEKey canonicalize(AEKey key) {
        if (!SHAREABLE || !(key.getPrimaryKey() instanceof MatterType)) return key;
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot = KeyReuse.slot(epoch, MatterKey.class, key.getPrimaryKey());
        if (slot == null) return key;
        AEKey cached = (AEKey) slot.get(epoch);
        if (cached != null) return key.equals(cached) ? cached : key;
        AEKey owned = of(((MatterKey) key).getStack());
        return key.equals(owned) ? owned : key;
    }
}
