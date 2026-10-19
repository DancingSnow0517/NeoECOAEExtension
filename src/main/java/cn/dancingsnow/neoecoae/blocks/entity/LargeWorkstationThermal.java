package cn.dancingsnow.neoecoae.blocks.entity;

import net.minecraft.nbt.CompoundTag;

/** Percentage-based thermal rules. Every step represents one loaded server tick, never offline time. */
public final class LargeWorkstationThermal {
    public static final int TICKS_PER_SECOND = 20;
    public static final int RECOVERY_TICKS = 15 * TICKS_PER_SECOND;
    public static final int COOLANT_PER_TICK = 100;
    public static final double PREHEAT_PER_SECOND = 2;
    public static final double PREHEAT_AE_PER_TICK = 1_000;

    private LargeWorkstationThermal() {}

    public record State(double temperature, boolean overheated, int recoveryTicks) {
        public State {
            temperature = Double.isFinite(temperature) ? Math.clamp(temperature, 0, 100) : 100;
            overheated = overheated || temperature >= 100;
            recoveryTicks = overheated ? Math.clamp(recoveryTicks, 0, RECOVERY_TICKS - 1) : 0;
            // A saved low temperature is never sufficient to bypass a latched overheat.
            if (overheated) temperature = 100.0 * (RECOVERY_TICKS - recoveryTicks) / RECOVERY_TICKS;
        }
    }

    public static double heatingPerSecond(double temperature) {
        double fraction = Math.clamp(temperature, 0, 100) / 100;
        return 0.2 + 0.8 * fraction * fraction;
    }

    public static double coolingPerSecond(int tier) {
        return switch (tier) { case 2 -> 0.1; case 6 -> 0.3; case 9 -> 0.6; default -> 0; };
    }

    /** Work is measured against a full batch, including multiplied-pattern base operations. */
    public static State process(State state, double load) {
        if (state.overheated || !Double.isFinite(load) || load <= 0) return state;
        return new State(state.temperature + heatingPerSecond(state.temperature) * load / TICKS_PER_SECOND, false, 0);
    }

    /** Recovery credits only a successfully consumed cryotheum tick. An interruption resets all credit. */
    public static State tick(State state, double preheatFraction, int coolantTier, boolean consumedCoolant) {
        if (state.overheated) {
            if (!consumedCoolant || coolantTier != 9) return new State(100, true, 0);
            int next = state.recoveryTicks + 1;
            return next >= RECOVERY_TICKS ? new State(0, false, 0)
                : new State(100.0 * (RECOVERY_TICKS - next) / RECOVERY_TICKS, true, next);
        }
        double heating = Double.isFinite(preheatFraction) ? PREHEAT_PER_SECOND * Math.clamp(preheatFraction, 0, 1) : 0;
        double cooling = consumedCoolant ? coolingPerSecond(coolantTier) : 0;
        return new State(state.temperature + (heating - cooling) / TICKS_PER_SECOND, false, 0);
    }

    public static void save(State state, CompoundTag tag) {
        tag.putDouble("temperature", state.temperature);
        tag.putBoolean("overheated", state.overheated);
        tag.putInt("recoveryTicks", state.recoveryTicks);
    }

    public static State load(CompoundTag tag) {
        // Reloading interrupts continuous coolant delivery; retain the latch but restart the 15-second run.
        boolean locked = tag.getBoolean("overheated") || tag.getDouble("temperature") >= 100;
        return new State(tag.getDouble("temperature"), locked, 0);
    }
}
