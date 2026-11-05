package cn.dancingsnow.neoecoae.dev;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import net.neoforged.fml.ModList;

/** No optional class is linked until the installed Applied Flux integration is requested. */
final class FeNetworkAccess {
    static boolean available() {
        var mods = ModList.get();
        return mods != null && mods.isLoaded("appflux");
    }

    static FeWorkload.Access create(MEStorage inventory, IActionSource source) {
        if (!available()) throw new IllegalStateException("Applied Flux is not loaded; FE workload is unavailable");
        return new Access(inventory, source);
    }

    private record Access(MEStorage inventory, IActionSource source) implements FeWorkload.Access {
        @Override public long insert(long amount, boolean simulate) {
            return inventory.insert(cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOFluxKeys.of(
                com.glodblock.github.appflux.common.me.key.type.EnergyType.FE), amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
        }
        @Override public long extract(long amount, boolean simulate) {
            return inventory.extract(cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOFluxKeys.of(
                com.glodblock.github.appflux.common.me.key.type.EnergyType.FE), amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
        }
        @Override public Boolean keyReuseEnabled() {
            return cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse.epoch() != null;
        }
    }
}
