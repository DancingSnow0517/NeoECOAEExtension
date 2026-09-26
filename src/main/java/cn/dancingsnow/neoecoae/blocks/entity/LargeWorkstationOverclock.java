package cn.dancingsnow.neoecoae.blocks.entity;

import org.jetbrains.annotations.Nullable;

/** Immutable per-batch overclock settings for the large integrated workstation. */
public record LargeWorkstationOverclock(int coolingTier, int maxParallelism, int energyMultiplier) {
    public static final int BASE_PARALLELISM = 1_024;
    public static final LargeWorkstationOverclock NORMAL = new LargeWorkstationOverclock(0, BASE_PARALLELISM, 1);

    public static LargeWorkstationOverclock forCurrentSettings(
        boolean overclocked,
        boolean activeCooling,
        int coolingTier
    ) {
        if (!overclocked || !activeCooling) {
            return NORMAL;
        }
        LargeWorkstationOverclock profile = forCoolingTier(coolingTier);
        return profile == null ? NORMAL : profile;
    }

    @Nullable
    public static LargeWorkstationOverclock forCoolingTier(int coolingTier) {
        return switch (coolingTier) {
            // These are the max_overclock values from the shared F-series cooling recipes.
            case 2 -> new LargeWorkstationOverclock(2, 16_384, 8);
            case 6 -> new LargeWorkstationOverclock(6, 65_536, 32);
            case 9 -> new LargeWorkstationOverclock(9, 262_144, 64);
            default -> null;
        };
    }

    @Nullable
    public static LargeWorkstationOverclock fromPersisted(int coolingTier, int energyMultiplier) {
        if (coolingTier == 0 && energyMultiplier == 1) {
            return NORMAL;
        }
        LargeWorkstationOverclock profile = forCoolingTier(coolingTier);
        return profile != null && profile.energyMultiplier == energyMultiplier ? profile : null;
    }

    public boolean isOverclocked() {
        return coolingTier > 0;
    }

    public boolean acceptsCraftCount(long craftCount) {
        return craftCount > 0 && craftCount <= maxParallelism;
    }
}
