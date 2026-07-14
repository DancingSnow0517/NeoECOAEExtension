package cn.dancingsnow.neoecoae.gui.ldlib.storage;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Client-side scroll positions keyed by player, dimension and storage host. */
public final class NEStorageScrollMemory {
    private static final Map<Key, Snapshot> MEMORY = new HashMap<>();

    public static Optional<Snapshot> restore(ECOStorageSystemBlockEntity storage, Player player) {
        return key(storage, player).map(MEMORY::get);
    }

    public static void remember(
            ECOStorageSystemBlockEntity storage,
            Player player,
            double leftScrollPixels,
            double hugeStackScrollPixels,
            int hugeStackPage) {
        key(storage, player)
                .ifPresent(key -> MEMORY.put(
                        key, new Snapshot(leftScrollPixels, hugeStackScrollPixels, Math.max(0, hugeStackPage))));
    }

    private static Optional<Key> key(ECOStorageSystemBlockEntity storage, Player player) {
        if (storage.getLevel() == null) {
            return Optional.empty();
        }
        return Optional.of(new Key(
                player.getUUID(),
                storage.getLevel().dimension().location(),
                storage.getBlockPos().immutable()));
    }

    public record Snapshot(double leftScrollPixels, double hugeStackScrollPixels, int hugeStackPage) {}

    private record Key(UUID playerId, ResourceLocation dimension, BlockPos pos) {}

    private NEStorageScrollMemory() {}
}
