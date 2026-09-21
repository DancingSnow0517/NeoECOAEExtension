package cn.dancingsnow.neoecoae.network;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOExactStoragePayloadTest {
    @Test
    void oneChangeDoesNotResendUnchangedKeysAndRemovalsClearOverrides() {
        Map<AEKey, BigInteger> before = new HashMap<>();
        for (int i = 0; i < 10_000; i++) before.put(new TestKey(), BigInteger.ONE.shiftLeft(100));
        var keys = before.keySet().iterator();
        AEKey changed = keys.next();
        AEKey removed = keys.next();
        AEKey added = new TestKey();
        Map<AEKey, BigInteger> after = new HashMap<>(before);
        after.put(changed, BigInteger.ONE.shiftLeft(101));
        after.remove(removed); // Includes crossing back into AE2's native long range.
        after.put(added, BigInteger.ONE.shiftLeft(110));
        var delta = ECOExactStoragePayload.difference(before, after);
        assertEquals(3, delta.amounts().size());
        assertEquals(BigInteger.ZERO, delta.amounts().get(removed));
        assertEquals(after, delta.apply(before));
        assertEquals(after, new ECOExactStoragePayload(true, after).apply(before));
        assertTrue(ECOExactStoragePayload.difference(after, after).amounts().isEmpty());
        assertTrue(new ECOExactStoragePayload(true, Map.of()).apply(after).isEmpty());
    }

    private static final class TestKey extends AEKey {
        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("test", "key");
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Component computeDisplayName() {
            return Component.empty();
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }

    @Test
    void emptySnapshotCanClearTheCurrentMenu() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ECOExactStoragePayload.encode(new ECOExactStoragePayload(true, Map.of()), buffer);
            var decoded = ECOExactStoragePayload.decode(buffer);
            assertTrue(decoded.reset());
            assertTrue(decoded.amounts().isEmpty());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void invalidEntryCountIsRejectedBeforeAllocatingOrReadingKeys() {
        for (int count : new int[] {-1, 1_000_001}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeBoolean(true);
                buffer.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> ECOExactStoragePayload.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }
}
