package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathKey;
import org.jetbrains.annotations.Nullable;

public final class ECOFastPathEligibility {
    public static boolean isGloballyEnabled() {
        return NEConfig.isEcoAe2FastPathEnabled();
    }

    public static boolean canUse(ECOExtractedPatternExecution execution) {
        return canUse(execution, execution.key());
    }

    public static boolean canUse(ECOExtractedPatternExecution execution, @Nullable ECOFastPathKey key) {
        return key != null && execution.fastPathEligible() && isGloballyEnabled();
    }

    private ECOFastPathEligibility() {}
}
