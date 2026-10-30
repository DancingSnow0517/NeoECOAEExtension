package cn.dancingsnow.neoecoae.blocks.entity;

import net.minecraft.nbt.CompoundTag;

/** Percentage-based thermal rules. Every step represents one loaded server tick, never offline time. */
public final class LargeWorkstationThermal {
    public static final int TICKS_PER_SECOND = 20;
    public static final int RECOVERY_TICKS = 15 * TICKS_PER_SECOND;
    public static final int COOLANT_PER_TICK = 100;
    public static final double PREHEAT_HEAT_MULTIPLIER = 2;
    public static final double PREHEAT_AE_PER_TICK = 1_000;
    public static final double IDLE_COOLING_PER_SECOND = 0.1;

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
        double remaining = 1 - Math.clamp(temperature, 0, 100) / 100;
        return 0.65 + 3.35 * remaining * remaining;
    }

    public static double preheatPerSecond(double temperature) {
        return PREHEAT_HEAT_MULTIPLIER * heatingPerSecond(temperature);
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
        return tick(state, 0, preheatFraction, coolantTier, consumedCoolant);
    }

    /** Settle processing heat and cooling together, so only the net change can trigger overheating. */
    public static State tick(State state, double processingLoad, double preheatFraction, int coolantTier, boolean consumedCoolant) {
        return step(state, processingLoad, preheatFraction, coolantTier, consumedCoolant).state;
    }

    /** Applied heat and cooling in percentage points for one server tick, before averaging for the UI. */
    public record TickResult(State state, double processingHeat, double preheatHeat, double cooling, double naturalCooling) {}

    /** A fully paid thermostat tick circulates coolant even when holding or heating its target. */
    record RegulationPlan(int coolantAmount, TickResult result) {}

    static RegulationPlan regulate(State state, double processingLoad, int target, int coolantTier, int minimumCoolant) {
        double coolingPerTick = coolingPerSecond(coolantTier) / TICKS_PER_SECOND;
        if (coolingPerTick <= 0 || minimumCoolant <= 0) {
            throw new IllegalArgumentException("Regulation requires a supported coolant and positive circulation");
        }
        if (state.overheated) {
            return new RegulationPlan(minimumCoolant, step(state, 0, 0, coolantTier, true));
        }
        double load = Double.isFinite(processingLoad) ? Math.max(0, processingLoad) : 0;
        double processing = heatingPerSecond(state.temperature) * load / TICKS_PER_SECOND;
        double natural = load == 0 ? Math.min(state.temperature, IDLE_COOLING_PER_SECOND / TICKS_PER_SECOND) : 0;
        double baseline = state.temperature + processing - natural;
        double boundedTarget = Math.clamp(target, 0, 99);
        double heating = 0;
        double cooling = 0;
        double next;
        if (baseline <= boundedTarget) {
            heating = Math.min(boundedTarget - baseline, preheatPerSecond(state.temperature) / TICKS_PER_SECOND);
            next = Math.min(boundedTarget, baseline + heating);
        } else {
            // Remove paid processing heat as well as lowering the temperature at the circulation rate.
            cooling = Math.min(baseline - boundedTarget,
                processing + coolingPerTick * minimumCoolant / COOLANT_PER_TICK);
            next = Math.max(boundedTarget, baseline - cooling);
        }
        double needed = Math.ceil(cooling / coolingPerTick * COOLANT_PER_TICK);
        int coolant = (int) Math.min(Integer.MAX_VALUE, Math.max(minimumCoolant, needed));
        return new RegulationPlan(coolant, new TickResult(new State(next, false, 0), processing, heating, cooling, natural));
    }

    public static TickResult step(State state, double processingLoad, double preheatFraction, int coolantTier, boolean consumedCoolant) {
        if (state.overheated) {
            if (!consumedCoolant || coolantTier != 9) return new TickResult(new State(100, true, 0), 0, 0, 0, 0);
            int next = state.recoveryTicks + 1;
            State recovered = next >= RECOVERY_TICKS ? new State(0, false, 0)
                : new State(100.0 * (RECOVERY_TICKS - next) / RECOVERY_TICKS, true, next);
            return new TickResult(recovered, 0, 0, state.temperature - recovered.temperature, 0);
        }
        double preheat = Double.isFinite(preheatFraction)
            ? preheatPerSecond(state.temperature) * Math.clamp(preheatFraction, 0, 1) / TICKS_PER_SECOND : 0;
        double load = Double.isFinite(processingLoad) ? Math.max(0, processingLoad) : 0;
        double processing = heatingPerSecond(state.temperature) * load / TICKS_PER_SECOND;
        double availableHeat = state.temperature + processing + preheat;
        double cooling = consumedCoolant ? Math.min(availableHeat, coolingPerSecond(coolantTier) / TICKS_PER_SECOND) : 0;
        double naturalCooling = load == 0 && preheat == 0
            ? Math.min(availableHeat - cooling, IDLE_COOLING_PER_SECOND / TICKS_PER_SECOND) : 0;
        State next = new State(availableHeat - cooling - naturalCooling, false, 0);
        return new TickResult(next, processing, preheat, cooling, naturalCooling);
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
