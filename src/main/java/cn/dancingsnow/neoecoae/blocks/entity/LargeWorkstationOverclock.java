package cn.dancingsnow.neoecoae.blocks.entity;

import org.jetbrains.annotations.Nullable;

/** Immutable per-batch overclock settings for the large integrated workstation. */
public record LargeWorkstationOverclock(int coolingTier, int maxParallelism, int energyMultiplier) {
    private static final int[] DESCENDING_TIERS = {9, 6, 2};
    public static final int BASE_PARALLELISM = 256;
    public static final LargeWorkstationOverclock NORMAL = new LargeWorkstationOverclock(0, BASE_PARALLELISM, 1);

    public static LargeWorkstationOverclock forTemperature(boolean overclocked, double temperature) {
        if (!overclocked) return NORMAL;
        for (int tier : DESCENDING_TIERS) {
            LargeWorkstationOverclock profile = forCoolingTier(tier);
            if (profile.isTemperatureReady(temperature)) return profile;
        }
        return NORMAL;
    }

    /** Legacy coolant mode has no temperature or selected-tier requirement. */
    public static LargeWorkstationOverclock forCoolant(boolean overclocked, boolean activeCooling, int coolingTier) {
        if (!overclocked || !activeCooling) return NORMAL;
        LargeWorkstationOverclock profile = forCoolingTier(coolingTier);
        return profile == null ? NORMAL : profile;
    }

    @Nullable
    public static LargeWorkstationOverclock forCoolingTier(int coolingTier) {
        return switch (coolingTier) {
            // These are the max_overclock values from the shared F-series cooling recipes.
            case 2 -> new LargeWorkstationOverclock(2, 16_384, 1);
            case 6 -> new LargeWorkstationOverclock(6, 65_536, 1);
            case 9 -> new LargeWorkstationOverclock(9, 262_144, 1);
            default -> null;
        };
    }

    @Nullable
    public static LargeWorkstationOverclock fromPersisted(int coolingTier, int energyMultiplier) {
        if (coolingTier == 0 && energyMultiplier == 1) {
            return NORMAL;
        }
        LargeWorkstationOverclock profile = forCoolingTier(coolingTier);
        int legacyMultiplier = switch (coolingTier) { case 2 -> 8; case 6 -> 32; case 9 -> 64; default -> -1; };
        return profile != null && (energyMultiplier == 1 || energyMultiplier == legacyMultiplier) ? profile : null;
    }

    public double minimumTemperature() {
        return switch (coolingTier) { case 2 -> 20; case 6 -> 50; case 9 -> 80; default -> 0; };
    }

    public boolean isTemperatureReady(double temperature) {
        return Double.isFinite(temperature) && temperature >= minimumTemperature() && temperature < 100;
    }

    public boolean isOverclocked() {
        return coolingTier > 0;
    }

    public boolean acceptsCraftCount(long craftCount) {
        return craftCount > 0 && craftCount <= maxParallelism;
    }
}
