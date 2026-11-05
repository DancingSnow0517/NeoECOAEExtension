package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEKey;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;

/** Loaded only by ECO's Applied Flux integration or after receiving an actual FluxKey. */
public final class ECOFluxKeys {
    private ECOFluxKeys() {}
    public static FluxKey of(EnergyType type) {
        Object epoch = KeyReuse.epoch();
        KeyReuseSlot slot = KeyReuse.slot(epoch, FluxKey.class, type);
        if (slot == null) return FluxKey.of(type);
        FluxKey key = (FluxKey) slot.get(epoch);
        if (key != null) return key;
        synchronized (slot) {
            key = (FluxKey) slot.get(epoch);
            if (key == null) {
                key = FluxKey.of(type);
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
