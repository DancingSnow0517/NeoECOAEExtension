package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;

/** Coalesces pattern bus provider updates made during the same server tick. */
public final class PatternBusUpdateScheduler {
    private static final PendingUpdates<ECOCraftingPatternBusBlockEntity> DIRTY = new PendingUpdates<>();

    private PatternBusUpdateScheduler() {}

    public static void mark(ECOCraftingPatternBusBlockEntity bus, int deadline) {
        DIRTY.mark(bus, deadline);
    }

    public static void remove(ECOCraftingPatternBusBlockEntity bus) {
        DIRTY.remove(bus);
    }

    public static void clear(MinecraftServer server) {
        DIRTY.removeIf(bus -> bus.getLevel() == null || bus.getLevel().getServer() == server);
    }

    public static void tick(MinecraftServer server) {
        List<ECOCraftingPatternBusBlockEntity> due = DIRTY.drainDue(
                server.getTickCount(),
                bus -> !bus.isRemoved()
                        && bus.getLevel() != null
                        && bus.getLevel().getServer() == server);
        for (var bus : due) bus.flushScheduledPatternDetails();
    }

    static final class PendingUpdates<T> {
        private final Reference2IntOpenHashMap<T> deadlines = new Reference2IntOpenHashMap<>();

        void mark(T target, int deadline) {
            deadlines.put(target, deadline);
        }

        void remove(T target) {
            deadlines.removeInt(target);
        }

        void removeIf(Predicate<T> predicate) {
            deadlines.keySet().removeIf(predicate);
        }

        List<T> drainDue(int now, Predicate<T> valid) {
            List<T> due = new ArrayList<>();
            for (var iterator = deadlines.reference2IntEntrySet().iterator(); iterator.hasNext(); ) {
                Reference2IntMap.Entry<T> entry = iterator.next();
                if (!valid.test(entry.getKey())) iterator.remove();
                else if (now >= entry.getIntValue()) {
                    due.add(entry.getKey());
                    iterator.remove();
                }
            }
            return due;
        }
    }
}
