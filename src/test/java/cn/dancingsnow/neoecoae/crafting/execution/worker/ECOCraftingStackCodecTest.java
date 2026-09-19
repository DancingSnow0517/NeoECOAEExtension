package cn.dancingsnow.neoecoae.crafting.execution.worker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOCraftingStackCodecTest {
    @Test
    void emptyCounterProducesNoRecoveryEntries() {
        KeyCounter counter = new KeyCounter();
        assertTrue(ECOCraftingStackCodec.isEmpty(counter));
        assertTrue(ECOCraftingStackCodec.toGenericStacks(counter, false).isEmpty());
    }

    @Test
    void unsupportedKeysCannotBePersistedAsWorkerRecoveryData() {
        KeyCounter counter = new KeyCounter();
        counter.add(new TestKey(), 1L);

        assertFalse(ECOCraftingStackCodec.isEmpty(counter));
        assertTrue(ECOCraftingStackCodec.toGenericStacks(counter, false).isEmpty());
        assertTrue(ECOCraftingStackCodec.toGenericStacks(counter, true).isEmpty());
    }

    @Test
    void retentionRejectsNullAndNonPositiveEntries() {
        TestKey key = new TestKey();
        assertFalse(ECOCraftingStackCodec.canRetain(List.of(new GenericStack(key, 0L)), false));
        assertFalse(ECOCraftingStackCodec.canRetain(java.util.Arrays.asList((GenericStack) null), false));
        assertFalse(ECOCraftingStackCodec.canRetain(List.of(new GenericStack(key, 1L)), true));
        assertTrue(ECOCraftingStackCodec.canRetain(List.of(), false));
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
        public net.minecraft.nbt.CompoundTag toTag() {
            return new net.minecraft.nbt.CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public net.minecraft.resources.ResourceLocation getId() {
            return null;
        }

        @Override
        public void writeToPacket(net.minecraft.network.FriendlyByteBuf buf) {}

        @Override
        protected net.minecraft.network.chat.Component computeDisplayName() {
            return net.minecraft.network.chat.Component.literal("test");
        }

        @Override
        public void addDrops(
                long amount,
                List<net.minecraft.world.item.ItemStack> drops,
                net.minecraft.world.level.Level level,
                net.minecraft.core.BlockPos pos) {}
    }
}
