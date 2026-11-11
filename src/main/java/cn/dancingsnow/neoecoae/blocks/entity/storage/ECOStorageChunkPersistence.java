package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/** Transfer barriers use vanilla's ordered chunk writer without its blocking save-all operation. */
final class ECOStorageChunkPersistence {
    private ECOStorageChunkPersistence() { }

    static CompletableFuture<Void> checkpoint(ECOStorageSystemBlockEntity host,
                                               Collection<ECODriveBlockEntity> drives) {
        if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Storage checkpoint requires the server thread"));
        }
        try {
            var required = new LinkedHashMap<ChunkPos, List<BlockPos>>();
            List<BlockEntity> entities = new ArrayList<>(drives);
            entities.add(host);
            for (BlockEntity entity : entities) {
                if (entity.getLevel() != level || entity.isRemoved()) {
                    throw new IllegalStateException("Storage checkpoint target is no longer loaded");
                }
                if (entity instanceof ECODriveBlockEntity drive) drive.flushPendingCellWrites();
                entity.setChanged();
                required.computeIfAbsent(new ChunkPos(entity.getBlockPos()), ignored -> new ArrayList<>())
                        .add(entity.getBlockPos());
            }
            var snapshots = new LinkedHashMap<ChunkPos, CompoundTag>();
            for (var entry : required.entrySet()) {
                var pos = entry.getKey();
                var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk == null) throw new IllegalStateException("Storage checkpoint chunk is no longer loaded");
                level.getPoiManager().flush(pos);
                CompoundTag snapshot = ChunkSerializer.write(level, chunk);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.level.ChunkDataEvent.Save(chunk, level, snapshot));
                for (BlockPos requiredPos : entry.getValue()) {
                    boolean saved = snapshot.getList("block_entities", Tag.TAG_COMPOUND).stream()
                            .map(CompoundTag.class::cast)
                            .anyMatch(tag -> tag.getInt("x") == requiredPos.getX()
                                    && tag.getInt("y") == requiredPos.getY() && tag.getInt("z") == requiredPos.getZ());
                    if (!saved) throw new IllegalStateException("Storage block entity was omitted from its chunk snapshot");
                }
                snapshots.put(pos, snapshot);
            }
            var writes = new ArrayList<CompletableFuture<Void>>();
            snapshots.forEach((pos, snapshot) -> writes.add(level.getChunkSource().chunkMap.write(pos, snapshot)));
            // Leave chunks dirty: later vanilla saves must still include any subsequent world changes.
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
