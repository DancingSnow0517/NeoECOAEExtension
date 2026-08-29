package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.util.NEMath;
import java.util.List;

/**
 * Authoritative, immutable view of an F-series crafting host or logical exchange network.
 *
 * <p>A physical FX worker is one execution lane. A lane can own one batch at a time; the number of crafts in
 * that batch is described by {@link Capacity}. FT parallel cores are deliberately reported separately and only
 * influence the ordinary progress/overclock model. They are never a second dispatch-capacity ceiling.</p>
 */
public record CraftingCapabilitySnapshot(
    int physicalFxCount,
    int activeFxCount,
    int normalSwitchHosts,
    int highEnergySwitchHosts,
    int networkMultiplier,
    Capacity batchPerFx,
    Capacity totalBatchCapacity,
    long ftParallelCapacity,
    int runningBatchCount,
    int theoreticalOverclock,
    int effectiveOverclock,
    boolean virtualEligible,
    boolean virtualMode,
    long energyUsage,
    CoolantState coolantState
) {
    public static final long BASE_BATCH_PER_FX = 32L;
    public static final long F9_OVERCLOCKED_BATCH_PER_FX = 512L;
    public static final int MAX_OVERCLOCK = 9;

    public CraftingCapabilitySnapshot {
        physicalFxCount = Math.max(0, physicalFxCount);
        activeFxCount = Math.clamp(activeFxCount, 0, physicalFxCount);
        normalSwitchHosts = Math.max(0, normalSwitchHosts);
        highEnergySwitchHosts = Math.max(0, highEnergySwitchHosts);
        networkMultiplier = Math.max(0, networkMultiplier);
        batchPerFx = java.util.Objects.requireNonNull(batchPerFx, "batchPerFx");
        totalBatchCapacity = java.util.Objects.requireNonNull(totalBatchCapacity, "totalBatchCapacity");
        ftParallelCapacity = Math.max(0L, ftParallelCapacity);
        runningBatchCount = Math.max(0, runningBatchCount);
        theoreticalOverclock = Math.clamp(theoreticalOverclock, 0, MAX_OVERCLOCK);
        effectiveOverclock = Math.clamp(effectiveOverclock, 0, theoreticalOverclock);
        energyUsage = Math.max(0L, energyUsage);
        coolantState = java.util.Objects.requireNonNull(coolantState, "coolantState");
    }

    /** Pure calculator used by both live topology code and unit tests. */
    public static CraftingCapabilitySnapshot calculate(Input input) {
        int physicalFx = Math.max(0, input.physicalFxCount());
        int normalHosts = Math.max(0, input.normalSwitchHosts());
        int highEnergyHosts = Math.max(0, input.highEnergySwitchHosts());
        long multiplierLong = NEMath.saturatingAdd(
            NEMath.saturatingMultiply(normalHosts, 2L),
            NEMath.saturatingMultiply(highEnergyHosts, 8L)
        );
        int multiplier = (int) Math.min(Integer.MAX_VALUE, multiplierLong);
        boolean exchange = normalHosts + highEnergyHosts >= 2 && multiplier > 0;

        boolean virtualEligible = input.virtualTopologyEligible();
        boolean virtualMode = virtualEligible;
        Capacity batchPerFx;
        Capacity totalCapacity;
        if (virtualMode) {
            batchPerFx = Capacity.unlimitedCapacity();
            totalCapacity = Capacity.unlimitedCapacity();
        } else {
            long finiteBatchPerFx = exchange
                ? NEMath.saturatingMultiply(F9_OVERCLOCKED_BATCH_PER_FX, multiplier)
                : input.overclocked()
                    ? Math.max(BASE_BATCH_PER_FX, input.standaloneOverclockedBatchPerFx())
                    : BASE_BATCH_PER_FX;
            batchPerFx = Capacity.finite(finiteBatchPerFx);
            totalCapacity = Capacity.finite(NEMath.saturatingMultiply(physicalFx, finiteBatchPerFx));
        }

        long baseFxCapacity = NEMath.saturatingMultiply(physicalFx, BASE_BATCH_PER_FX);
        int theoretical = calculateOverclock(input.ftParallelCapacity(), baseFxCapacity);
        int effective = input.overclocked()
            ? (input.activeCooling()
                ? Math.min(theoretical, Math.max(0, input.coolantState().maxSupportedOverclock()))
                : theoretical)
            : 0;
        long energyUsage = virtualMode
            ? NECraftingNetworkCluster.VIRTUAL_CRAFTING_POWER_PER_TICK
            : NEMath.saturatingMultiply(totalCapacity.finiteValue(), 100L);
        if (input.overclocked() && !input.activeCooling() && !virtualMode) {
            energyUsage = NEMath.saturatingMultiply(energyUsage, Math.max(1L, input.overclockPowerMultiplier()));
        }
        return new CraftingCapabilitySnapshot(
            physicalFx,
            input.activeFxCount(),
            normalHosts,
            highEnergyHosts,
            multiplier,
            batchPerFx,
            totalCapacity,
            input.ftParallelCapacity(),
            input.runningBatchCount(),
            theoretical,
            effective,
            virtualEligible,
            virtualMode,
            energyUsage,
            input.coolantState()
        );
    }

    static int calculateOverclock(long ftParallelCapacity, long baseFxCapacity) {
        long ft = Math.max(0L, ftParallelCapacity);
        long overflow = ft - Math.max(0L, baseFxCapacity);
        if (ft <= 0L || overflow <= 0L) {
            return 0;
        }
        return (int) Math.clamp(Math.round(((double) overflow / (double) ft) / 0.05D), 0L, MAX_OVERCLOCK);
    }

    public static boolean isVirtualTopologyEligible(List<VirtualHost> hosts) {
        if (hosts == null || hosts.size() != 8) {
            return false;
        }
        for (VirtualHost host : hosts) {
            if (host == null || !host.f9() || !host.highEnergySwitch()
                || host.actualFxCount() != host.requiredFxCount()) {
                return false;
            }
        }
        return true;
    }

    public record VirtualHost(
        boolean f9,
        boolean highEnergySwitch,
        int actualFxCount,
        int requiredFxCount
    ) {}

    public record Input(
        int physicalFxCount,
        int activeFxCount,
        int normalSwitchHosts,
        int highEnergySwitchHosts,
        long standaloneOverclockedBatchPerFx,
        long ftParallelCapacity,
        int runningBatchCount,
        boolean overclocked,
        boolean activeCooling,
        int overclockPowerMultiplier,
        boolean virtualTopologyEligible,
        CoolantState coolantState
    ) {}

    /** Explicit finite/unlimited capacity; unlimited is never encoded as an integer sentinel. */
    public record Capacity(boolean unlimited, long finiteValue) {
        public Capacity {
            finiteValue = Math.max(0L, finiteValue);
            if (unlimited) {
                finiteValue = 0L;
            }
        }

        public static Capacity finite(long value) {
            return new Capacity(false, value);
        }

        public static Capacity unlimitedCapacity() {
            return new Capacity(true, 0L);
        }
    }

    public record CoolantState(
        boolean activeCooling,
        long amount,
        long capacity,
        int maxSupportedOverclock
    ) {
        public CoolantState {
            amount = Math.max(0L, amount);
            capacity = Math.max(0L, capacity);
            maxSupportedOverclock = Math.max(-1, maxSupportedOverclock);
        }
    }
}
