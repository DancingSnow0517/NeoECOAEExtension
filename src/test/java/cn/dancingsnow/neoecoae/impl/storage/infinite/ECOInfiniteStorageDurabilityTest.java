package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ECOInfiniteStorageDurabilityTest {
    // Exercise real journal I/O without the AE2 key-type registry installed by the mod launcher.
    private static final ECOInfiniteStorageData.KeyCodec ITEM_CODEC = new ECOInfiniteStorageData.KeyCodec() {
        @Override
        public CompoundTag encode(AEKey key) {
            var tag = new CompoundTag();
            tag.putString("item", BuiltInRegistries.ITEM.getKey(((AEItemKey) key).getItem()).toString());
            return tag;
        }

        @Override
        public AEKey decode(CompoundTag tag) {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(tag.getString("item")))
                    .map(AEItemKey::of).orElse(null);
        }
    };

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void ordinaryIoJournalReplaysAnAcknowledgedChange(@TempDir Path directory) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var snapshot = directory.resolve("domain.dat");
        var emptySnapshot = ECOInfiniteStorageData.createNew().save(new CompoundTag(), RegistryAccess.EMPTY);
        var live = ECOInfiniteStorageData.load(emptySnapshot, RegistryAccess.EMPTY, ITEM_CODEC);
        assertTrue(live.appendJournalChange(snapshot.toFile(), RegistryAccess.EMPTY, key, 64L, true));

        var recovered = ECOInfiniteStorageData.load(emptySnapshot, RegistryAccess.EMPTY, ITEM_CODEC);
        ECOInfiniteStorageData.replayJournal(recovered, snapshot, RegistryAccess.EMPTY);

        assertEquals(HugeAmount.of(64L), recovered.getAmount(key));
    }

    @Test
    void hugeRestoreFinishesInLongSizedSegments() {
        var key = AEItemKey.of(Items.STONE);
        var data = ECOInfiniteStorageData.createNew();
        data.amounts.set(key, HugeAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(5L))));
        UUID transaction = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        var goal = new ECOInfiniteStorageEngine.RestoreTargetAmounts(0L, Long.MAX_VALUE, Long.MAX_VALUE);

        assertTrue(data.reserveRestore(key, transaction, Set.of(target), Map.of(target, goal)));
        assertTrue(data.finishRestore(key, transaction));
        assertEquals(HugeAmount.of(5L), data.getAmount(key));
    }
}
