package cn.dancingsnow.neoecoae.integration.megacells.backend;

import net.neoforged.fml.ModList;

/**
 * Boundary for the optional MEGA Cells implementation.
 *
 * <p>MEGA Cells 4.11.0 has no public extension API. NeoECOAE therefore uses AE2's storage
 * interfaces and MEGA Cells' stable registry ids in recipes, without linking implementation classes.</p>
 */
public final class MegaCellsBackend {
    public static final String MOD_ID = "megacells";
    public static final String VERIFIED_VERSION = "4.11.0";
    public static final String APPFLUX_MOD_ID = "appflux";
    public static final String APPMEK_MOD_ID = "appmek";
    public static final String MEKANISM_MOD_ID = "mekanism";

    private MegaCellsBackend() {
    }

    public static boolean isEnergyAvailable() {
        return ModList.get().isLoaded(APPFLUX_MOD_ID);
    }

    public static boolean isChemicalAvailable() {
        return ModList.get().isLoaded(APPMEK_MOD_ID) && ModList.get().isLoaded(MEKANISM_MOD_ID);
    }
}
