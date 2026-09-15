package cn.dancingsnow.neoecoae.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ECOExactStoragePayloadTest {
    @Test
    void emptySnapshotCanClearTheCurrentMenu() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ECOExactStoragePayload.encode(new ECOExactStoragePayload(42, Map.of()), buffer);
            var decoded = ECOExactStoragePayload.decode(buffer);
            assertEquals(42, decoded.containerId());
            assertTrue(decoded.amounts().isEmpty());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void invalidEntryCountIsRejectedBeforeAllocatingOrReadingKeys() {
        for (int count : new int[] {-1, 65537}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(42);
                buffer.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> ECOExactStoragePayload.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }
}
