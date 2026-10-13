package cn.dancingsnow.neoecoae.integration.appmek;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;

/**
 * Storage filter shared by the Mekanism chemical cells.
 */
public final class MekanismCellFilter {
    private MekanismCellFilter() {
    }

    /**
     * Returns {@code true} if {@code what} must not be stored: any non-Mekanism key, or a chemical
     * rejected by Mekanism's default {@link ChemicalAttributeValidator}.
     */
    public static boolean isBlackListed(AEKey what) {
        if (what instanceof MekanismKey key) {
            return !ChemicalAttributeValidator.DEFAULT.process(key.getStack());
        }
        return true;
    }
}
