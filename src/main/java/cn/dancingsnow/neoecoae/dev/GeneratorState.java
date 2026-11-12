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
    NetworkTrafficRecorder recording;

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
        if (recording != null) recording.tick();
        if (fe != null) {
            if (!feRunning) return;
            try {
                var access = FeNetworkAccess.create(grid.getStorageService().getInventory(), source);
                fe.tick(recording == null ? access : recording.wrap(access));
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
            long amount = settings.nextAmount(random);
            var inventory = grid.getStorageService().getInventory();
            long inserted = recording == null ? inventory.insert(key, amount, Actionable.MODULATE, source)
                : recording.insert(inventory, key, amount, Actionable.MODULATE, source);
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
        for (AEKey key : selected) keys.add(cn.dancingsnow.neoecoae.crafting.ECOLegacyAENbt.writeKey(registries, key));
        tag.put("keys", keys);
        return tag;
    }

    static GeneratorState read(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new GeneratorState();
        if (!tag.contains("types")) return state;
        state.settings = new GeneratorSettings(tag.getIntOr("types", 0), tag.getLongOr("minimum", 0L), tag.getLongOr("maximum", 0L), tag.getIntOr("interval", 0));
        if (tag.contains("fe")) {
            var energy = tag.getCompoundOrEmpty("fe");
            state.fe = new FeWorkload(new FeWorkloadSettings(energy.getIntOr("producers", 0), energy.getLongOr("produceMin", 0L), energy.getLongOr("produceMax", 0L),
                energy.getIntOr("consumers", 0), energy.getLongOr("consumeMin", 0L), energy.getLongOr("consumeMax", 0L), energy.getLongOr("seed", 0L), energy.getBooleanOr("simulateFirst", false)));
            state.feRunning = energy.getBooleanOr("running", false);
        }
        for (Tag entry : tag.getListOrEmpty("keys")) {
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
