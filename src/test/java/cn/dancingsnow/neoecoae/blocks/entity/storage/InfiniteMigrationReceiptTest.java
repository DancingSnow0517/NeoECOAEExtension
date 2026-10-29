package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InfiniteMigrationReceiptTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void controllerReceiptKeepsItsPersistedFormula() {
        UUID domain = UUID.fromString("0b6f3c8e-7d0a-4b9e-9a4c-2f51d6a1c001");
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.getOrCreateTag().putString("name", "receipt");
        AEKey[] keys = {AEItemKey.of(Items.STONE), AEItemKey.of(stack)};
        BlockPos pos = new BlockPos(-12, 64, 300);
        for (AEKey key : keys) {
            for (String direction : new String[] {"to-domain", "from-domain"}) {
                long amount = 123_456_789L;
                // The exact text hashed before the change; these receipts are persisted to prevent re-imports.
                String original = domain + ":" + direction + ":" + pos.asLong() + ":" + key.toTagGeneric() + ":" + amount;
                UUID expected = UUID.nameUUIDFromBytes(original.getBytes(StandardCharsets.UTF_8));
                assertEquals(expected, ECOInfiniteStorageModeController.legacyTransactionId(domain, direction, pos.asLong(),
                    ECOInfiniteStorageTransfer.encodedKey(key), amount));
            }
        }
    }
}
