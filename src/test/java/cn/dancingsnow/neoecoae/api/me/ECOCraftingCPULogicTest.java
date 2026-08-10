package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import java.lang.reflect.Field;
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
import org.junit.jupiter.api.Test;

class ECOCraftingCPULogicTest {
    @Test
    void batchRequestPreservesTheRemainingTaskAmount() {
        assertEquals(512L, ECOCraftingCPULogic.calculateBatchRequestSize(512L));
        assertEquals(0L, ECOCraftingCPULogic.calculateBatchRequestSize(-1L));
        assertEquals(Long.MAX_VALUE, ECOCraftingCPULogic.calculateBatchRequestSize(Long.MAX_VALUE));
    }

    @Test
    void slowPathOperationLimitIsBoundedByCoprocessorsAndConfiguration() {
        assertEquals(1, ECOCraftingCPULogic.calculateOperationLimit(-1, 64));
        assertEquals(5, ECOCraftingCPULogic.calculateOperationLimit(4, 64));
        assertEquals(3, ECOCraftingCPULogic.calculateOperationLimit(64, 3));
        assertEquals(0, ECOCraftingCPULogic.calculateOperationLimit(64, -1));
    }

    @Test
    void plannedInputsAreOnlyUsedBySlowPathDispatch() {
        assertTrue(ECOCraftingCPULogic.shouldUsePlannedInputsForDispatch(false, true, 8L, 8L));
        assertFalse(ECOCraftingCPULogic.shouldUsePlannedInputsForDispatch(true, true, 8L, 8L));
        assertFalse(ECOCraftingCPULogic.shouldUsePlannedInputsForDispatch(false, false, 8L, 8L));
        assertFalse(ECOCraftingCPULogic.shouldUsePlannedInputsForDispatch(false, true, 9L, 8L));
    }

    @Test
    void scaledPatternAmountSaturatesWithoutTurningNegative() {
        assertEquals(0L, ECOCraftingCPULogic.scaledPatternAmount(0L, 8L));
        assertEquals(8L, ECOCraftingCPULogic.scaledPatternAmount(4L, 2L));
        assertEquals(4L, ECOCraftingCPULogic.scaledPatternAmount(4L, 0L));
        assertEquals(Long.MAX_VALUE, ECOCraftingCPULogic.scaledPatternAmount(Long.MAX_VALUE, 2L));
    }

    @Test
    void remainingJobOutputAmountExposesTheCurrentJobAmount() throws Exception {
        ECOCraftingCPULogic logic = testLogic();

        assertEquals(0L, logic.getRemainingJobOutputAmount());

        setJob(logic, jobWithRemainingAmount(42L));
        assertEquals(42L, logic.getRemainingJobOutputAmount());

        setJob(logic, jobWithRemainingAmount(-7L));
        assertEquals(-7L, logic.getRemainingJobOutputAmount());
    }

    @Test
    void userPauseIsSeparateFromInternalSuspension() throws Exception {
        ECOCraftingCPULogic logic = testLogic();
        setJob(logic, jobWithRemainingAmount(1L));

        assertFalse(logic.isJobUserPaused());
        assertFalse(logic.isJobSuspended());

        logic.setJobUserPaused(true);
        assertTrue(logic.isJobUserPaused());
        assertFalse(logic.isJobSuspended());

        logic.toggleJobUserPaused();
        assertFalse(logic.isJobUserPaused());
        assertFalse(logic.isJobSuspended());
    }

    private static ExecutingCraftingJob jobWithRemainingAmount(long remainingAmount) throws Exception {
        CraftingLink link = new CraftingLink(
                CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), (ICraftingCPU) null);
        ExecutingCraftingJob job = new ExecutingCraftingJob(new TestCraftingPlan(), ignored -> {}, link, null);
        job.remainingAmount = remainingAmount;
        return job;
    }

    private static ECOCraftingCPULogic testLogic() {
        return new ECOCraftingCPU(null, 0L, null).getLogic();
    }

    private static void setJob(ECOCraftingCPULogic logic, ExecutingCraftingJob job) throws Exception {
        Field field = ECOCraftingCPULogic.class.getDeclaredField("job");
        field.setAccessible(true);
        field.set(logic, job);
    }

    private static final class TestCraftingPlan implements ICraftingPlan {
        @Override
        public GenericStack finalOutput() {
            return new GenericStack(TestKey.INSTANCE, 1);
        }

        @Override
        public long bytes() {
            return 0;
        }

        @Override
        public boolean simulation() {
            return false;
        }

        @Override
        public boolean multiplePaths() {
            return false;
        }

        @Override
        public KeyCounter usedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter emittedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter missingItems() {
            return new KeyCounter();
        }

        @Override
        public Map<IPatternDetails, Long> patternTimes() {
            return Map.of();
        }
    }

    private static final class TestKey extends AEKey {
        private static final TestKey INSTANCE = new TestKey();
        private static final TestKeyType TYPE = new TestKeyType();
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("neoecoae", "test");

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
            return ID;
        }

        @Override
        public void writeToPacket(FriendlyByteBuf data) {}

        @Override
        protected Component computeDisplayName() {
            return Component.literal("test");
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("neoecoae", "test"), TestKey.class, Component.literal("test"));
        }

        @Override
        public AEKey readFromPacket(FriendlyByteBuf input) {
            return TestKey.INSTANCE;
        }

        @Override
        public AEKey loadKeyFromTag(CompoundTag tag) {
            return TestKey.INSTANCE;
        }
    }
}
