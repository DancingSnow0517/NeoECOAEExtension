package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;

/** Loaded only when using chemical cells; preserves Applied Mekanistics' chemical safety checks. */
final class BiggerAE2ChemicalValidation {
    private BiggerAE2ChemicalValidation() {}

    static boolean accepts(AEKey what) {
        return what instanceof MekanismKey key && ChemicalAttributeValidator.DEFAULT.process(key.getStack());
    }
}
