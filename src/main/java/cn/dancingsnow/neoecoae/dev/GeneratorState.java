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

final class GeneratorState {
    GeneratorSettings settings = GeneratorSettings.DEFAULT;
    final List<AEKey> selected = new ArrayList<>();
    private final Random random = new Random();
    private int elapsed;
    long accepted;

    void reroll() {
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
        if (changeTypes) reroll();
        elapsed = 0;
    }

    void tick(IGrid grid, IActionSource source, Runnable save) {
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
        var keys = new ListTag();
        for (AEKey key : selected) keys.add(key.toTagGeneric());
        tag.put("keys", keys);
        return tag;
    }

    static GeneratorState read(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new GeneratorState();
        if (!tag.contains("types")) return state;
        state.settings = new GeneratorSettings(tag.getInt("types"), tag.getLong("minimum"), tag.getLong("maximum"), tag.getInt("interval"));
        for (Tag entry : tag.getList("keys", Tag.TAG_COMPOUND)) {
            AEKey key = ECOKeyFactory.fromTagGeneric((CompoundTag) entry);
            if (key == null) throw new IllegalStateException("Dev generator resource is no longer registered: " + entry);
            state.selected.add(key);
        }
        return state;
    }

    String describe() {
        return "X=" + settings.types() + ", amount=" + settings.minimum() + ".." + settings.maximum()
            + ", interval=" + settings.interval() + " ticks, selected=" + selected.size()
            + ", last accepted=" + accepted;
    }
}
