package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import java.util.Set;
import java.util.function.Predicate;

/** Optional EAEP smart-blocking support for ECO's specialized dispatch paths. */
public final class ECOExtendedAEPlusBlocking {
    private ECOExtendedAEPlusBlocking() {}

    /** Reads the setting registered by EAEP without loading any optional mod classes. */
    public static boolean isEnabled(IConfigManager configManager) {
        for (var setting : configManager.getSettings()) {
            if ("advanced_blocking".equals(setting.getName()) && setting.getEnumClass().equals(YesNo.class)) {
                return YesNo.YES.equals(setting.getValue(configManager));
            }
        }
        return false;
    }

    /** Mirrors EAEP: each input must have a candidate present; quantities and extra inputs are ignored. */
    public static boolean matchesPendingInputs(IPatternDetails pattern, Predicate<Set<AEKey>> containsInput) {
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null || inputs.length == 0) {
            return false;
        }
        for (IPatternDetails.IInput input : inputs) {
            if (input == null || input.getPossibleInputs() == null) {
                return false;
            }
            boolean matched = false;
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (candidate != null && candidate.what() != null
                        && containsInput.test(Set.of(candidate.what().dropSecondary()))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }
}
