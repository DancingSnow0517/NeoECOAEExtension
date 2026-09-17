package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class InfiniteStorageJournalRecordTest {
    @Test
    void reusedFrameIsReadableByExistingNbtReader() throws Exception {
        var key = new CompoundTag();
        key.putString("id", "appflux:fe");
        var nested = new CompoundTag();
        nested.putString("unicode", "能量");
        key.put("components", nested);
        var record = new InfiniteStorageJournalRecord(key);
        for (int i = 0; i < 100; i++) {
            long amount = i % 2 == 0 ? Long.MAX_VALUE : i;
            var frame = record.prepare(i + 1L, amount, i % 2 == 0);
            byte[] bytes = new byte[frame.remaining()];
            frame.get(bytes); // Consume the same buffer as a channel would.
            try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                assertEquals(bytes.length - Integer.BYTES, in.readInt());
                CompoundTag decoded = NbtIo.read(in, NbtAccounter.unlimitedHeap());
                assertEquals(i + 1L, decoded.getLong("sequence"));
                assertEquals(amount, decoded.getLong("amount"));
                assertEquals(i % 2 == 0, decoded.getBoolean("added"));
                assertEquals(key, decoded.getCompound("key"));
                assertEquals(-1, in.read());
            }
        }
    }
}
