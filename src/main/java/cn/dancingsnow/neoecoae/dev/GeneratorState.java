package cn.dancingsnow.neoecoae.dev;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers.grouped;
import static cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers.compact;

final class GeneratorState {
    GeneratorSettings settings = GeneratorSettings.DEFAULT;
    final List<AEKey> selected = new ArrayList<>();
    private final Random random = new Random();
    private int elapsed;
    long accepted;
    FeWorkload fe;
    boolean feRunning;
    String feFailure;

    void reroll() {
        if (fe != null) throw new IllegalArgumentException("FE uses one energy type; use set to return to resource generation");
        var pool = new ArrayList<>(GeneratorPool.keys());
        if (settings.types() > pool.size()) throw new IllegalArgumentException("Only " + pool.size() + " plain resources are available");
        Collections.shuffle(pool, random);
        selected.clear();
        selected.addAll(pool.subList(0, settings.types()));
        elapsed = 0;
    }

    void configure(GeneratorSettings replacement) {
        if (replacement.types() > GeneratorPool.keys().size()) throw new IllegalArgumentException("Too many resource types");
        boolean changeTypes = replacement.types() != settings.types() || selected.isEmpty();
        settings = replacement;
        fe = null;
        feRunning = false;
        feFailure = null;
        if (changeTypes) reroll();
        elapsed = 0;
    }

    void tick(IGrid grid, IActionSource source, Runnable save) {
        if (fe != null) {
            if (!feRunning) return;
            try {
                fe.tick(FeNetworkAccess.create(grid.getStorageService().getInventory(), source));
            } catch (RuntimeException | LinkageError error) {
                feRunning = false;
                feFailure = error.toString();
                save.run();
                org.slf4j.LoggerFactory.getLogger(GeneratorState.class).warn("FE workload paused without retry", error);
            }
            return;
        }
        if (++elapsed < settings.interval()) return;
        elapsed = 0;
        if (selected.isEmpty()) {
            reroll();
            save.run();
        }
        accepted = 0;
        for (AEKey key : selected) {
            long inserted = grid.getStorageService().getInventory().insert(key, settings.nextAmount(random), Actionable.MODULATE, source);
            accepted = inserted > Long.MAX_VALUE - accepted ? Long.MAX_VALUE : accepted + inserted;
        }
        // Rejected output is discarded. There is no virtual stock, retry backlog or refund.
    }

    CompoundTag write(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("types", settings.types());
        tag.putLong("minimum", settings.minimum());
        tag.putLong("maximum", settings.maximum());
        tag.putInt("interval", settings.interval());
        if (fe != null) {
            var config = fe.settings;
            var energy = new CompoundTag();
            energy.putInt("producers", config.producers());
            energy.putLong("produceMin", config.produceMin());
            energy.putLong("produceMax", config.produceMax());
            energy.putInt("consumers", config.consumers());
            energy.putLong("consumeMin", config.consumeMin());
            energy.putLong("consumeMax", config.consumeMax());
            energy.putLong("seed", config.seed());
            energy.putBoolean("simulateFirst", config.simulateFirst());
            energy.putBoolean("running", feRunning);
            tag.put("fe", energy);
        }
        var keys = new ListTag();
        for (AEKey key : selected) keys.add(key.toTagGeneric(registries));
        tag.put("keys", keys);
        return tag;
    }

    static GeneratorState read(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new GeneratorState();
        if (!tag.contains("types")) return state;
        state.settings = new GeneratorSettings(tag.getInt("types"), tag.getLong("minimum"), tag.getLong("maximum"), tag.getInt("interval"));
        if (tag.contains("fe")) {
            var energy = tag.getCompound("fe");
            state.fe = new FeWorkload(new FeWorkloadSettings(energy.getInt("producers"), energy.getLong("produceMin"), energy.getLong("produceMax"),
                energy.getInt("consumers"), energy.getLong("consumeMin"), energy.getLong("consumeMax"), energy.getLong("seed"), energy.getBoolean("simulateFirst")));
            state.feRunning = energy.getBoolean("running");
        }
        for (Tag entry : tag.getList("keys", Tag.TAG_COMPOUND)) {
            AEKey key = ECOKeyFactory.fromTagGeneric(registries, (CompoundTag) entry);
            if (key == null) throw new IllegalStateException("Dev generator resource is no longer registered: " + entry);
            state.selected.add(key);
        }
        return state;
    }

    String describe() {
        if (fe != null) return (feRunning ? "RUNNING " : "PAUSED ") + fe.describe() + (feFailure == null ? "" : ", failure=" + feFailure);
        return "X=" + grouped(settings.types()) + ", amount=" + compact(settings.minimum()) + ".." + compact(settings.maximum())
            + ", interval=" + grouped(settings.interval()) + " ticks, selected=" + grouped(selected.size())
            + ", last accepted=" + compact(accepted);
    }

    void configureFe(FeWorkloadSettings config) {
        if (!FeNetworkAccess.available()) throw new IllegalArgumentException("Applied Flux is not loaded; FE workload is unavailable");
        fe = new FeWorkload(config);
        feRunning = true;
        feFailure = null;
        selected.clear();
        elapsed = 0;
    }

    void controlFe(String action) {
        if (fe == null) throw new IllegalArgumentException("Configure FE first with /neodev fe");
        if (action.equals("fe_stop")) feRunning = false;
        if (action.equals("fe_start")) {
            if (feFailure != null) throw new IllegalArgumentException("Reset the failed run with fe_reset before resuming");
            feRunning = true;
        }
        if (action.equals("fe_reset")) {
            fe = new FeWorkload(fe.settings);
            feFailure = null;
        }
    }
}
