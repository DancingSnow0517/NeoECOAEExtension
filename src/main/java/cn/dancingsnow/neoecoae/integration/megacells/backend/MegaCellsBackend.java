package cn.dancingsnow.neoecoae.integration.megacells.backend;

import cn.dancingsnow.neoecoae.integration.ModIds;

/**
 * Boundary for the optional MEGA Cells implementation.
 *
 * <p>MEGA Cells 4.11.0 has no formal extension API. All direct implementation linkage is kept
 * behind this package and is loaded only with the conditional integration.</p>
 */
public final class MegaCellsBackend {
    private MegaCellsBackend() {
    }

    public static boolean isEnergyAvailable() {
        return ModIds.isLoaded(ModIds.APPFLUX);
    }

    public static boolean isChemicalAvailable() {
        return ModIds.isLoaded(ModIds.APPMEK) && ModIds.isLoaded(ModIds.MEKANISM);
    }
}
