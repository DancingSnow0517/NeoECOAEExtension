package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.stacks.AEKey;
import gripe._90.megacells.definition.MEGAConfig;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import mekanism.common.registries.MekanismChemicals;
import net.neoforged.fml.ModList;

/**
 * Boundary for the optional MEGA Cells implementation.
 *
 * <p>MEGA Cells 4.11.0 has no formal extension API. All direct implementation linkage is kept
 * behind this package and is loaded only with the conditional integration.</p>
 */
public final class MegaCellsBackend {
    public static final String MOD_ID = "megacells";
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

    /** Matches MEGA Cells' radioactive-cell policy, including its spent-waste setting. */
    public static boolean acceptsRadioactiveChemical(AEKey what) {
        if (!(what instanceof MekanismKey key)) return false;
        if (key.getStack().getChemical() == MekanismChemicals.SPENT_NUCLEAR_WASTE.get()) {
            return MEGAConfig.CONFIG.isSpentWasteAllowed();
        }
        return !ChemicalAttributeValidator.DEFAULT.process(key.getStack());
    }
}
