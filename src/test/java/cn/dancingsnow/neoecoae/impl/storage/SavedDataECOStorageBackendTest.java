package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavedDataECOStorageBackendTest {
    @TempDir
    Path directory;

    @AfterEach
    void clearRegistrations() {
        ECOSavedDataPersistence.clear();
    }

    @Test
    void simulatedIoDoesNotChangeQuantitiesRevisionOrSnapshot() throws Exception {
        var backend = create();
        var key = key(backend, 1);
        assertEquals(1000, backend.insert(key, 1000, Actionable.MODULATE));
        var before = backend.save(new CompoundTag());
        long revision = backend.getRevision();
        assertEquals(4000, backend.insert(key, 4000, Actionable.SIMULATE));
        assertEquals(1000, backend.extract(key, 2000, Actionable.SIMULATE));
        assertEquals(revision, backend.getRevision());
        assertEquals(before, backend.save(new CompoundTag()));
        assertEquals(Map.of(key, 1000L), backend.copyContents());
    }

    @Test
    void sustainedIoRemovalAndRefillKeepIndexesAndSnapshotOrder() throws Exception {
        var backend = create();
        var first = key(backend, 1);
        var second = key(backend, 2);
        backend.insert(first, 1000, Actionable.MODULATE);
        backend.insert(second, 2000, Actionable.MODULATE);
        for (int i = 0; i < 10000; i++) {
            assertEquals(64, backend.extract(first, 64, Actionable.MODULATE));
            assertEquals(64, backend.insert(first, 64, Actionable.MODULATE));
        }
        var snapshot = backend.save(new CompoundTag());
        var entries = snapshot.getList("entries", 10);
        assertEquals(1000, entries.getCompound(0).getLong("amount"));
        assertEquals(2000, entries.getCompound(1).getLong("amount"));
        assertEquals(1000, backend.extract(first, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(0, backend.getAmount(first));
        assertEquals(1, backend.getStoredTypes());
        cacheEncoding(backend, first);
        backend.insert(first, 7, Actionable.MODULATE);
        KeyCounter visible = new KeyCounter();
        backend.getAvailableStacks(visible);
        assertEquals(7, visible.get(first));
        assertEquals(2000, visible.get(second));
        assertEquals(2007, backend.getStoredAmount().toLongSaturated());
        entries = backend.save(new CompoundTag()).getList("entries", 10);
        assertEquals(2000, entries.getCompound(0).getLong("amount"));
        assertEquals(7, entries.getCompound(1).getLong("amount"));
        // A later write must not mutate a previously captured save.
        assertEquals(1000, snapshot.getList("entries", 10).getCompound(0).getLong("amount"));
        assertEquals(Map.of(first, 7L, second, 2000L), backend.copyContents());
    }

    private SavedDataECOStorageBackend create() {
        return SavedDataECOStorageBackend.createNew(UUID.randomUUID(), null, directory.resolve("cell.dat"));
    }

    private static TestKey key(SavedDataECOStorageBackend backend, int id) throws Exception {
        var key = new TestKey(id);
        cacheEncoding(backend, key);
        return key;
    }

    @SuppressWarnings("unchecked")
    private static void cacheEncoding(SavedDataECOStorageBackend backend, TestKey key) throws Exception {
        // This unit fixture bypasses the Forge key registry; game tests cover real keys.
        var field = SavedDataECOStorageBackend.class.getDeclaredField("encodedKeys");
        field.setAccessible(true);
        ((Map<AEKey, CompoundTag>) field.get(backend)).put(key, key.toTag());
    }

    private static final class TestKey extends AEKey {
        private final int id;

        private TestKey(int id) {
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("test", "item_" + id);
        }

        @Override
        public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putInt("id", id);
            return tag;
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {}

        @Override
        protected Component computeDisplayName() {
            return Component.literal("test");
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }
}
