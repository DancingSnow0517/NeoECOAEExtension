package cn.dancingsnow.neoecoae.crafting.graph;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.network.ExactMapSync;
import cn.dancingsnow.neoecoae.network.MapDelta;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Live execution state, keyed by the original snapshot's material IDs, independent of its layout. */
public record CraftingGraphProgress(Set<Integer> running, Set<Integer> completed, Map<Integer, Amounts> amounts,
        boolean paused, boolean cancelled) {
    public static final CraftingGraphProgress EMPTY = new CraftingGraphProgress(Set.of(), Set.of());

    public record Amounts(BigInteger stored, BigInteger active, BigInteger pending, BigInteger produced) {
        private void write(RegistryFriendlyByteBuf buf) {
            ExactMapSync.writeInteger(buf, stored);
            ExactMapSync.writeInteger(buf, active);
            ExactMapSync.writeInteger(buf, pending);
            ExactMapSync.writeInteger(buf, produced);
        }

        private static Amounts read(RegistryFriendlyByteBuf buf) {
            return new Amounts(ExactMapSync.readInteger(buf), ExactMapSync.readInteger(buf),
                ExactMapSync.readInteger(buf), ExactMapSync.readInteger(buf));
        }
    }

    public CraftingGraphProgress(Set<Integer> running, Set<Integer> completed) {
        this(running, completed, Map.of(), false, false);
    }

    public CraftingGraphProgress {
        running = Set.copyOf(running);
        completed = Set.copyOf(completed);
        amounts = Map.copyOf(amounts);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        write(buf, null);
    }

    public void write(RegistryFriendlyByteBuf buf, CraftingGraphProgress previous) {
        buf.writeVarIntArray(running.stream().mapToInt(Integer::intValue).toArray());
        buf.writeVarIntArray(completed.stream().mapToInt(Integer::intValue).toArray());
        buf.writeBoolean(paused);
        buf.writeBoolean(cancelled);
        buf.writeBoolean(previous == null);
        var delta = MapDelta.between(previous == null ? Map.of() : previous.amounts, amounts);
        buf.writeVarInt(delta.updates().size());
        delta.updates().forEach((id, value) -> { buf.writeVarInt(id); value.write(buf); });
        buf.writeVarIntArray(delta.removed().stream().mapToInt(Integer::intValue).toArray());
    }

    public static CraftingGraphProgress read(RegistryFriendlyByteBuf buf) {
        return read(buf, EMPTY);
    }

    public static CraftingGraphProgress read(RegistryFriendlyByteBuf buf, CraftingGraphProgress previous) {
        Set<Integer> running = new HashSet<>(), completed = new HashSet<>();
        for (int id : buf.readVarIntArray(1_000_000)) running.add(id);
        for (int id : buf.readVarIntArray(1_000_000)) completed.add(id);
        boolean paused = buf.readBoolean(), cancelled = buf.readBoolean(), full = buf.readBoolean();
        Map<Integer, Amounts> amounts = full ? new HashMap<>() : new HashMap<>(previous.amounts);
        int count = buf.readVarInt();
        if (count < 0 || count > 1_000_000) throw new IllegalArgumentException("Invalid graph amount count");
        for (int i = 0; i < count; i++) amounts.put(buf.readVarInt(), Amounts.read(buf));
        for (int id : buf.readVarIntArray(1_000_000)) amounts.remove(id);
        return new CraftingGraphProgress(running, completed, amounts, paused, cancelled);
    }
}
