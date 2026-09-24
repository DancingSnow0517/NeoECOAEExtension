package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Coalesces pattern bus updates without allocating one server task per mutation. */
public final class PatternBusUpdateScheduler {
    private static final Reference2IntOpenHashMap<ECOCraftingPatternBusBlockEntity> DIRTY =
            new Reference2IntOpenHashMap<>();
    private PatternBusUpdateScheduler() {}
    public static void mark(ECOCraftingPatternBusBlockEntity bus, int deadline) { DIRTY.put(bus, deadline); }
    public static void remove(ECOCraftingPatternBusBlockEntity bus) { DIRTY.removeInt(bus); }
    public static void clear(MinecraftServer server) {
        DIRTY.keySet().removeIf(bus -> bus.getLevel() == null || bus.getLevel().getServer() == server);
    }
    public static void tick(MinecraftServer server) {
        if (DIRTY.isEmpty()) return;
        int now = server.getTickCount();
        List<ECOCraftingPatternBusBlockEntity> due = null;
        for (ObjectIterator<Reference2IntMap.Entry<ECOCraftingPatternBusBlockEntity>> it =
                DIRTY.reference2IntEntrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            var bus = entry.getKey();
            if (bus.isRemoved() || bus.getLevel() == null || bus.getLevel().getServer() != server) it.remove();
            else if (now >= entry.getIntValue()) {
                it.remove();
                if (due == null) due = new ArrayList<>();
                due.add(bus);
            }
        }
        // Flushing can mark or remove other buses; fastutil iterators are not fail-fast, so never flush mid-iteration.
        if (due != null) for (var bus : due) bus.flushScheduledPatternDetails();
    }
}
