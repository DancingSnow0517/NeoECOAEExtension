package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicSavedDataFileTest {
    @TempDir
    Path directory;

    @Test
    void serializationFailureKeepsLastCommittedInventory() throws Exception {
        Path path = directory.resolve("cell.dat");
        CompoundTag data = new CompoundTag();
        data.putLong("amount", 123);
        AtomicSavedDataFile.write(path, data, 3465);
        byte[] committed = Files.readAllBytes(path);
        data.putString("bad", "x".repeat(70000));
        assertThrows(IOException.class, () -> AtomicSavedDataFile.write(path, data, 3465));
        assertArrayEquals(committed, Files.readAllBytes(path));
        assertEquals(123, AtomicSavedDataFile.read(path).getLong("amount"));
        try (var paths = Files.list(directory)) {
            assertEquals(1, paths.count());
        }
    }
}
