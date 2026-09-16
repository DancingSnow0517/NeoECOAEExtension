package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import cn.dancingsnow.neoecoae.mixins.minecraft.ChunkMapAccessor;

/**
 * Narrow durability barrier for chunks participating in a storage ownership transfer.
 */
final class ECOStorageDurability {
    private ECOStorageDurability() {
    }

    static void saveChunks(ServerLevel level, Collection<BlockPos> positions) {
        Set<ChunkPos> saved = new HashSet<>();
        var chunkMap = level.getChunkSource().chunkMap;
        for (BlockPos position : positions) {
            ChunkPos chunkPos = new ChunkPos(position);
            if (!saved.add(chunkPos)) continue;
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            chunk.setUnsaved(true);
            if (!((ChunkMapAccessor) chunkMap).neoecoae$save(chunk) && chunk.isUnsaved()) {
                throw new IllegalStateException("Could not persist storage transaction chunk " + chunkPos);
            }
        }
        // Wait only for the writes queued above; unlike ServerChunkCache.save(true), this does
        // not enumerate, serialize, or unload unrelated chunks.
        chunkMap.flushWorker();
    }
}
