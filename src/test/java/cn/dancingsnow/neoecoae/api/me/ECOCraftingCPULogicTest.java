package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.config.NEConfig;
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
    void batchBudgetTracksTheWholeTick() {
        var budget = new ECOCraftingCPULogic.FastPathBatchBudget(5632);

        budget.consume(512);
        budget.consume(4608);

        assertEquals(512, budget.remaining());
        assertThrows(IllegalArgumentException.class, () -> budget.consume(513));
        assertEquals(512, budget.remaining());
    }

    @Test
    void batchBudgetIsIndependentFromSlowOperationBudget() {
        assertEquals(64, NEConfig.ecoBatchFastPathLimit);
        assertEquals(256, NEConfig.ecoBatchFastPathTickLimit);
        assertEquals(64, NEConfig.getEcoFastPathBatchLimit());
        assertEquals(256, NEConfig.getEcoFastPathTickLimit());
        assertEquals(256, ECOCraftingCPULogic.totalPatternBudget(64, 256));
    }

    @Test
    void aggressiveFastPathUsesSeparateOptInLimits() {
        boolean previousFastPath = NEConfig.enableEcoAe2FastPath;
        boolean previousPostCraftingEvent = NEConfig.postCraftingEvent;
        boolean previousAggressive = NEConfig.enableEcoAggressiveFastPath;
        int previousAggressiveLimit = NEConfig.ecoAggressiveFastPathLimit;
        int previousAggressiveTickLimit = NEConfig.ecoAggressiveFastPathTickLimit;

        try {
            NEConfig.enableEcoAe2FastPath = true;
            NEConfig.postCraftingEvent = false;
            NEConfig.enableEcoAggressiveFastPath = true;
            NEConfig.ecoAggressiveFastPathLimit = 4096;
            NEConfig.ecoAggressiveFastPathTickLimit = 4096;

            assertEquals(4096, NEConfig.getEcoFastPathBatchLimit());
            assertEquals(4096, NEConfig.getEcoFastPathTickLimit());
        } finally {
            NEConfig.enableEcoAe2FastPath = previousFastPath;
            NEConfig.postCraftingEvent = previousPostCraftingEvent;
            NEConfig.enableEcoAggressiveFastPath = previousAggressive;
            NEConfig.ecoAggressiveFastPathLimit = previousAggressiveLimit;
            NEConfig.ecoAggressiveFastPathTickLimit = previousAggressiveTickLimit;
        }
    }

    @Test
    void aggressiveSimulatedCraftPowerCapScalesWithTickLimit() {
        int previousAggressiveTickLimit = NEConfig.ecoAggressiveFastPathTickLimit;

        try {
            NEConfig.ecoAggressiveFastPathTickLimit = 4096;

            assertEquals(409_600.0D, ECOCraftingCPULogic.aggressiveSimulatedCraftPowerCap(100, 1));
            assertEquals(409_600.0D, ECOCraftingCPULogic.aggressiveSimulatedCraftPowerNeed(100, 1, 4096));
            assertEquals(409_600.0D, ECOCraftingCPULogic.aggressiveSimulatedCraftPowerNeed(100, 1, 5632));

            NEConfig.ecoAggressiveFastPathTickLimit = 16384;

            assertEquals(1_638_400.0D, ECOCraftingCPULogic.aggressiveSimulatedCraftPowerCap(100, 1));
            assertEquals(563_200.0D, ECOCraftingCPULogic.aggressiveSimulatedCraftPowerNeed(100, 1, 5632));
            assertEquals(1_638_400.0D, ECOCraftingCPULogic.aggressiveSimulatedCraftPowerNeed(100, 1, 16384));
        } finally {
            NEConfig.ecoAggressiveFastPathTickLimit = previousAggressiveTickLimit;
        }
    }

    @Test
    void aggressiveFastPathFallsBackWhenBaseFastPathIsDisabled() {
        boolean previousFastPath = NEConfig.enableEcoAe2FastPath;
        boolean previousPostCraftingEvent = NEConfig.postCraftingEvent;
        boolean previousAggressive = NEConfig.enableEcoAggressiveFastPath;

        try {
            NEConfig.enableEcoAe2FastPath = false;
            NEConfig.postCraftingEvent = false;
            NEConfig.enableEcoAggressiveFastPath = true;

            assertEquals(64, NEConfig.getEcoFastPathBatchLimit());
            assertEquals(256, NEConfig.getEcoFastPathTickLimit());
        } finally {
            NEConfig.enableEcoAe2FastPath = previousFastPath;
            NEConfig.postCraftingEvent = previousPostCraftingEvent;
            NEConfig.enableEcoAggressiveFastPath = previousAggressive;
        }
    }

    @Test
    void aggressiveFastPathRequiresEligibleFastPathExecution() {
        assertFalse(ECOCraftingCPULogic.canAttemptAggressiveFastPath(ECOExtractedPatternExecution.slow(null, null)));
    }

    @Test
    void aggressiveCoolantCostScalesWithBatchSize() {
        assertEquals(5, ECOCraftingCPULogic.coolantAmountForCrafts(0));
        assertEquals(5, ECOCraftingCPULogic.coolantAmountForCrafts(1));
        assertEquals(320, ECOCraftingCPULogic.coolantAmountForCrafts(64));
        assertEquals(Integer.MAX_VALUE, ECOCraftingCPULogic.coolantAmountForCrafts(Integer.MAX_VALUE));
    }

    @Test
    void finalOutputBatchLimitAccountsForInFlightOutputs() {
        assertEquals(5328, ECOCraftingCPULogic.maxCraftsForFinalOutputDemand(100_000, 94_672, 1));
        assertEquals(0, ECOCraftingCPULogic.maxCraftsForFinalOutputDemand(100_000, 100_000, 1));
        assertEquals(1, ECOCraftingCPULogic.maxCraftsForFinalOutputDemand(100_000, 99_999, 4));
        assertEquals(Integer.MAX_VALUE, ECOCraftingCPULogic.maxCraftsForFinalOutputDemand(100, 0, 0));
    }

    @Test
    void remainingJobOutputAmountUsesRemainingAmount() throws Exception {
        ECOCraftingCPULogic logic = new ECOCraftingCPULogic(null);

        assertEquals(0L, logic.getRemainingJobOutputAmount());

        setJob(logic, jobWithRemainingAmount(42L));
        assertEquals(42L, logic.getRemainingJobOutputAmount());

        setJob(logic, jobWithRemainingAmount(-7L));
        assertEquals(0L, logic.getRemainingJobOutputAmount());
    }

    @Test
    void userPauseIsSeparateFromInternalSuspension() throws Exception {
        ECOCraftingCPULogic logic = new ECOCraftingCPULogic(null);
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
        ExecutingCraftingJob job = new ExecutingCraftingJob(new TestCraftingPlan(), ignored -> {}, link, null, null);
        job.remainingAmount = remainingAmount;
        return job;
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
