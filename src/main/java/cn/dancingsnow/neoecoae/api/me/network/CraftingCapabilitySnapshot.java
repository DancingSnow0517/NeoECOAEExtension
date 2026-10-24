package cn.dancingsnow.neoecoae.api.me.network;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
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
        activeFxCount = JdkCompat.clamp(activeFxCount, 0, physicalFxCount);
        normalSwitchHosts = Math.max(0, normalSwitchHosts);
        highEnergySwitchHosts = Math.max(0, highEnergySwitchHosts);
        networkMultiplier = Math.max(0, networkMultiplier);
        batchPerFx = java.util.Objects.requireNonNull(batchPerFx, "batchPerFx");
        totalBatchCapacity = java.util.Objects.requireNonNull(totalBatchCapacity, "totalBatchCapacity");
        ftParallelCapacity = Math.max(0L, ftParallelCapacity);
        runningBatchCount = Math.max(0, runningBatchCount);
        theoreticalOverclock = JdkCompat.clamp(theoreticalOverclock, 0, MAX_OVERCLOCK);
        effectiveOverclock = JdkCompat.clamp(effectiveOverclock, 0, theoreticalOverclock);
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
        boolean exchange = (long) normalHosts + highEnergyHosts >= 2 && multiplier > 0;
        // Input is the sum of the hosts' local FT values. Apply the shared exchange multiplier once,
        // just as for batch capacity, so expanding a network preserves its ordinary timing ratio.
        long ftParallel = Math.max(0L, input.ftParallelCapacity());
        if (exchange) {
            ftParallel = NEMath.saturatingMultiply(ftParallel, multiplier);
        }

        boolean virtualEligible = input.virtualTopologyEligible();
        // Unlimited virtual crafting is an explicitly enabled endgame mode: the complete physical
        // topology is necessary but not sufficient.  Both controls must be on so the mode cannot
        // silently bypass the player's overclock/cooling choice.
        boolean virtualMode = virtualEligible && input.overclocked() && input.activeCooling();
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

        int theoretical = totalCapacity.unlimited() ? 0
            : calculateOverclock(ftParallel, totalCapacity.finiteValue());
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
            ftParallel,
            input.runningBatchCount(),
            theoretical,
            effective,
            virtualEligible,
            virtualMode,
            energyUsage,
            input.coolantState()
        );
    }

    /** 与面板 Limit 和普通耗时超频统一使用当前实际批量容量；虚拟模式独立执行。 */
    public long overflowCapacity() {
        return totalBatchCapacity.unlimited() ? 0L
            : calculateOverflow(ftParallelCapacity, totalBatchCapacity.finiteValue());
    }

    private static long calculateOverflow(long ftParallelCapacity, long batchCapacity) {
        return Math.max(0L, Math.max(0L, ftParallelCapacity) - Math.max(0L, batchCapacity));
    }

    static int calculateOverclock(long ftParallelCapacity, long batchCapacity) {
        long ft = Math.max(0L, ftParallelCapacity);
        long overflow = calculateOverflow(ft, batchCapacity);
        if (ft <= 0L || overflow <= 0L) {
            return 0;
        }
        return (int) JdkCompat.clamp(Math.round(((double) overflow / (double) ft) / 0.05D), 0L, MAX_OVERCLOCK);
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

    /** FT capacity is local/unmultiplied; the calculator applies any exchange multiplier. */
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
