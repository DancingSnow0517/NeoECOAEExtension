package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.stacks.AEKey;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;

import java.lang.reflect.Method;

/** Loaded only when using chemical cells; preserves Applied Mekanistics' chemical safety checks. */
final class BiggerAE2ChemicalValidation {
    // TODO(26.1 port): Applied Mekanistics has no 26.1 compile dependency; MekanismKey is resolved reflectively.
    private static final String MEKANISM_KEY = "me.ramidzkh.mekae2.ae2.MekanismKey";
    private static volatile Method getStack;

    private BiggerAE2ChemicalValidation() {}

    static boolean accepts(AEKey what) {
        if (what == null || !MEKANISM_KEY.equals(what.getClass().getName())) {
            return false;
        }
        try {
            Method method = getStack;
            if (method == null) {
                method = what.getClass().getMethod("getStack");
                getStack = method;
            }
            return method.invoke(what) instanceof ChemicalStack stack
                && ChemicalAttributeValidator.DEFAULT.process(stack);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }
}
