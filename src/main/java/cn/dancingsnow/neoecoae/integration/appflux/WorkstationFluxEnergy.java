package cn.dancingsnow.neoecoae.integration.appflux;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraftforge.fml.ModList;

/** Keeps optional classes behind a real mod-load guard. Only FE, never GT EU or AE network power. */
public final class WorkstationFluxEnergy {
    private WorkstationFluxEnergy() {}

    public static boolean available() {
        var mods = ModList.get();
        if (mods == null || !mods.isLoaded("appflux")) return false;
        try { return Access.KEY != null; }
        catch (LinkageError unavailable) { return false; }
    }

    public static long extract(MEStorage storage, long amount, Actionable mode, IActionSource source) {
        return amount <= 0 || !available() ? 0 : storage.extract(Access.KEY, amount, mode, source);
    }

    private static final class Access {
        private static final AEKey KEY = com.glodblock.github.appflux.common.me.key.FluxKey.of(
            com.glodblock.github.appflux.common.me.key.type.EnergyType.FE);
    }
}
