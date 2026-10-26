package cn.dancingsnow.neoecoae.integration.ae2lt;

import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

/** Resolves the matrix substitution without loading AE2LT classes for ordinary recipe inputs. */
public final class WorkstationLightningInputs {
    private WorkstationLightningInputs() {}

    @Nullable
    public static GenericStack highVoltageSubstitute(GenericStack input) {
        if (input.amount() <= 0
            || !input.what().getClass().getName().equals("com.moakiee.ae2lt.me.key.LightningKey")) return null;
        try {
            return Access.highVoltageSubstitute(input);
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    private static final class Access {
        @Nullable
        static GenericStack highVoltageSubstitute(GenericStack input) {
            if (!input.what().equals(com.moakiee.ae2lt.me.key.LightningKey.EXTREME_HIGH_VOLTAGE)) return null;
            long amount = Math.multiplyExact(input.amount(),
                (long) com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeService.EXTREME_TO_HIGH_RATIO);
            return new GenericStack(com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE, amount);
        }
    }
}
