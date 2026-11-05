package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEKey;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;

/** Loaded only by ECO's Applied Flux integration or after receiving an actual FluxKey. */
public final class ECOFluxKeys {
    private static final KeyReuseSlot[] SLOTS = new KeyReuseSlot[EnergyType.values().length];
    static {
        java.util.Arrays.setAll(SLOTS, ignored -> new KeyReuseSlot());
    }

    private ECOFluxKeys() {}
    public static FluxKey of(EnergyType type) {
        return getOrCreate(type, FluxKey::of);
    }

    /** Shared by ECO callers and Applied Flux's constructor redirect. The constructor is used only on a miss. */
    public static FluxKey getOrCreate(EnergyType type, java.util.function.Function<EnergyType, FluxKey> factory) {
        if (type == null) return null;
        Object epoch = KeyReuse.epoch();
        if (epoch == null) return factory.apply(type);
        KeyReuseSlot slot = SLOTS[type.ordinal()];
        FluxKey key = (FluxKey) slot.get(epoch);
        if (key != null) return key;
        synchronized (slot) {
            key = (FluxKey) slot.get(epoch);
            if (key == null) {
                key = factory.apply(type);
                slot.set(epoch, key);
            }
            return key;
        }
    }
    static AEKey canonicalize(AEKey key) {
        AEKey owned = of((EnergyType) key.getPrimaryKey());
        return key.equals(owned) ? owned : key;
    }
}
