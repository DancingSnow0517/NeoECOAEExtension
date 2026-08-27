package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.upgrades.Upgrades;
import gripe._90.megacells.definition.MEGAItems;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;

/**
 * Boundary for the optional MEGA Cells implementation.
 *
 * <p>MEGA Cells 4.11.0 has no formal extension API. All direct implementation linkage is kept
 * behind this package and is loaded only with the conditional integration.</p>
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

    public static void registerCompressionCard(ItemLike target, String group) {
        Upgrades.add(MEGAItems.COMPRESSION_CARD, target, 1, group);
    }
}
