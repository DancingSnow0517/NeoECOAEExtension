package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("Requires AE2 key deserialization through the Minecraft/Forge registry bootstrap.")
class FileBackedInfiniteStorageEngineTest {
    @TempDir
    private Path tempDir;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void closeAfterWalOnlyRecoveryPersistsRecoveredShards() {
        UUID domainId = UUID.randomUUID();
        Path domainPath = tempDir.resolve("domain");
        AEItemKey key = AEItemKey.of(Items.DIAMOND);

        FileBackedInfiniteStorageEngine writer = new FileBackedInfiniteStorageEngine(domainId, domainPath);
        writer.insert(key, 123L, Actionable.MODULATE);

        FileBackedInfiniteStorageEngine recovered = new FileBackedInfiniteStorageEngine(domainId, domainPath);
        assertEquals(HugeAmount.of(123L), recovered.getAmount(key));

        recovered.closeAndFlush();

        FileBackedInfiniteStorageEngine reopened = new FileBackedInfiniteStorageEngine(domainId, domainPath);
        assertEquals(HugeAmount.of(123L), reopened.getAmount(key));
    }
}
