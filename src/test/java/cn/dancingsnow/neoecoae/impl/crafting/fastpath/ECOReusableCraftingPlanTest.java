package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOReusableCraftingPlanTest {
    @Test
    void leasesExactInputRemainingMatchOnce() {
        var tool = new TestKey("tool");
        var material = new TestKey("material");
        var container = new TestKey("container");

        var plan = ECOReusableCraftingPlan.of(
                List.of(new GenericStack(tool, 1), new GenericStack(material, 2)),
                List.of(new GenericStack(tool, 1), new GenericStack(container, 1)));

        assertEquals(List.of(new GenericStack(material, 2)), plan.consumedInputsPerCraft());
        assertEquals(List.of(new GenericStack(tool, 1)), plan.reusableInputs());
        assertEquals(List.of(new GenericStack(container, 1)), plan.ordinaryRemainingPerCraft());
        assertEquals(List.of(new GenericStack(material, 6)), plan.batchInputs(3));
        assertEquals(List.of(new GenericStack(container, 3)), plan.batchRemaining(3));
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final ResourceLocation id;

        private TestKey(String path) {
            id = ResourceLocation.fromNamespaceAndPath("neoecoae", path);
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
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
            return id;
        }

        @Override
        public void writeToPacket(FriendlyByteBuf data) {}

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id.getPath());
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(
                    ResourceLocation.fromNamespaceAndPath("neoecoae", "reusable_test"),
                    TestKey.class,
                    Component.literal("test"));
        }

        @Override
        public AEKey readFromPacket(FriendlyByteBuf input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AEKey loadKeyFromTag(CompoundTag tag) {
            throw new UnsupportedOperationException();
        }
    }
}
