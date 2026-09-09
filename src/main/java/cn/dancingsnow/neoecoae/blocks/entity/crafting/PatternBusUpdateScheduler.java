package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import net.minecraft.server.MinecraftServer;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Coalesces pattern bus updates without allocating one server task per mutation. */
public final class PatternBusUpdateScheduler {
    private static final Map<ECOCraftingPatternBusBlockEntity, Integer> DIRTY = new IdentityHashMap<>();
    private PatternBusUpdateScheduler() {}
    public static void mark(ECOCraftingPatternBusBlockEntity bus, int deadline) { DIRTY.put(bus, deadline); }
    public static void remove(ECOCraftingPatternBusBlockEntity bus) { DIRTY.remove(bus); }
    public static void clear(MinecraftServer server) {
        DIRTY.keySet().removeIf(bus -> bus.getLevel() == null || bus.getLevel().getServer() == server);
    }
    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (Iterator<Map.Entry<ECOCraftingPatternBusBlockEntity, Integer>> it = DIRTY.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            var bus = entry.getKey();
            if (bus.isRemoved() || bus.getLevel() == null || bus.getLevel().getServer() != server) it.remove();
            else if (now >= entry.getValue()) { it.remove(); bus.flushScheduledPatternDetails(); }
        }
    }
}
