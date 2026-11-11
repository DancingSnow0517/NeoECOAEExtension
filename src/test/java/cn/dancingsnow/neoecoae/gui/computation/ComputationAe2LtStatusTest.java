package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtCpuStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import com.moakiee.ae2lt.crafting.big.BigCraftingCpu;
import com.moakiee.ae2lt.crafting.big.BigCraftingJob;
import com.moakiee.ae2lt.crafting.big.BigCraftingStatus;
import com.moakiee.ae2lt.crafting.big.BigStatusEntry;
import com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import com.moakiee.thunderbolt.core.crafting.planner.CraftPattern;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises the ECO page's real server snapshot, including its item and running-task payloads. */
class ComputationAe2LtStatusTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            Class.forName(cn.dancingsnow.neoecoae.NeoECOAE.class.getName());
            com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries.init();
        }
    }

    private Object previousMissingContentItem;
    @org.junit.jupiter.api.BeforeEach void bindFallbackItem() throws Exception {
        var field = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        field.setAccessible(true);
        previousMissingContentItem = field.get(appeng.core.definitions.AEItems.MISSING_CONTENT);
        var item = mock(net.neoforged.neoforge.registries.DeferredItem.class);
        when(item.get()).thenReturn(Items.BARRIER);
        when(item.asItem()).thenReturn(Items.BARRIER);
        field.set(appeng.core.definitions.AEItems.MISSING_CONTENT, item);
    }
    @org.junit.jupiter.api.AfterEach void restoreFallbackItem() throws Exception {
        var field = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        field.setAccessible(true);
        field.set(appeng.core.definitions.AEItems.MISSING_CONTENT, previousMissingContentItem);
    }

    @Test void ordinaryTianshuShowsStoredActivePendingAndWaitingOnlyKeys() throws Exception {
        try (var mods = mockStatic(ModList.class)) {
            loaded(mods, true);
            var cpu = mock(TimeWheelCraftingCPU.class);
            var logic = mock(Ae2LtTimeWheelCraftingCpuLogic.class);
            when(cpu.getCraftingLogic()).thenReturn(logic);
            var stone = AEItemKey.of(Items.STONE);
            var iron = AEItemKey.of(Items.IRON_INGOT);
            var gold = AEItemKey.of(Items.GOLD_INGOT);
            when(cpu.isBusy()).thenReturn(true);
            when(cpu.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
            when(logic.hasJob()).thenReturn(true);
            when(logic.getFinalJobOutput()).thenReturn(new GenericStack(iron, 100));
            when(logic.getRemainingOutputAmount()).thenReturn(70L);
            when(logic.getStored(stone)).thenReturn(40L);
            when(logic.getPendingOutputs(iron)).thenReturn(70L);
            when(logic.getWaitingFor(gold)).thenReturn(10L);
            doAnswer(call -> { ((KeyCounter) call.getArgument(0)).add(stone, 40); return null; })
                .when(logic).getAllItems(any());
            doAnswer(call -> {
                @SuppressWarnings("unchecked") var keys = (java.util.Set<appeng.api.stacks.AEKey>) call.getArgument(0);
                keys.add(iron); keys.add(gold); return null;
            }).when(logic).getAllWaitingFor(any());
            var fixture = new Fixture(cpu);
            var tag = fixture.snapshot();
            assertEquals(3, tag.getInt("itemTotal"));
            assertEquals(1, tag.getInt("taskTotal"));
            assertAmount(tag, "items", "stored", BigInteger.valueOf(40));
            assertAmount(tag, "items", "pending", BigInteger.valueOf(70));
            assertAmount(tag, "tasks", "active", BigInteger.TEN);
            var summary = ComputationCpuEntry.sample(cpu, 1);
            assertEquals(BigInteger.valueOf(100), summary.requested());
            assertEquals(BigInteger.valueOf(70), summary.remaining());
            when(logic.isJobSuspended()).thenReturn(true);
            assertEquals("suspended", ComputationCpuEntry.sample(cpu, 1).status());
            toggle(cpu);
            verify(logic).setJobSuspended(false);
            // Sampling and rendering never move resources or submit work.
            verify(logic, never()).insert(any(), anyLong(), any());
            verify(cpu, never()).cancelJob();
        }
    }

    @Test void exactTianshuKeepsAmountsBeyondLongAndUsesUpstreamStatusAccounting() throws Exception {
        try (var mods = mockStatic(ModList.class); var upstream = mockStatic(BigCraftingStatus.class)) {
            loaded(mods, true);
            var cpu = mock(BigCraftingCpu.class);
            var key = AEItemKey.of(Items.GOLD_INGOT);
            var exact = BigInteger.TEN.pow(80);
            var recipe = CraftPattern.<AEKey>tagConversion(AEItemKey.of(Items.STONE), key, new Object());
            var block = BigExecutionProgram.block(List.of(new BigExecutionProgram.Step<>(recipe, exact)), BigInteger.ONE);
            var program = BigExecutionProgram.of(List.of(block, block));
            var job = new BigCraftingJob(key, exact, program);
            job.blockIndex = 1;
            when(cpu.isBusy()).thenReturn(true);
            when(cpu.job()).thenReturn(job);
            when(cpu.getSelectionMode()).thenReturn(CpuSelectionMode.PLAYER_ONLY);
            when(cpu.getJobStatus()).thenReturn(new CraftingJobStatus(new GenericStack(key, Long.MAX_VALUE), 2, 1, 100));
            // AE2LT supplies these attached amounts via its runtime Mixin; reproduce that attachment in plain JVM tests.
            var entry = new ExactEntry(key, new BigStatusEntry.Amounts(exact, exact.add(BigInteger.ONE), exact.multiply(BigInteger.TWO)));
            upstream.when(() -> BigCraftingStatus.create(cpu)).thenReturn(new CraftingStatus(true, 100, 1, 2, List.of(entry), false));
            var fixture = new Fixture(cpu);
            var tag = fixture.snapshot();
            assertEquals(1, tag.getInt("itemTotal"));
            assertEquals(1, tag.getInt("taskTotal"));
            assertAmount(tag, "items", "stored", exact);
            assertAmount(tag, "items", "active", exact.add(BigInteger.ONE));
            assertAmount(tag, "tasks", "pending", exact.multiply(BigInteger.TWO));
            var summary = ComputationCpuEntry.sample(cpu, 1);
            assertEquals(exact, summary.requested());
            assertEquals(exact.divide(BigInteger.TWO), summary.remaining());
            job.suspended = true;
            assertEquals("suspended", ComputationCpuEntry.sample(cpu, 1).status());
            toggle(cpu);
            verify(cpu).toggleSuspended();
            job.suspended = false;
            job.returning = true;
            assertEquals("returning", ComputationCpuEntry.sample(cpu, 1).status());
            upstream.when(() -> BigCraftingStatus.create(cpu)).thenReturn(CraftingStatus.EMPTY);
            when(cpu.isBusy()).thenReturn(false);
            fixture.tick.addAndGet(10);
            var finished = fixture.snapshot();
            assertEquals(0, finished.getInt("itemTotal"));
            assertEquals(0, finished.getInt("taskTotal"));
            assertEquals("idle", ComputationCpuEntry.sample(cpu, 1).status());
        }
    }

    @Test void absentModAndUnknownCpuHaveNoOptionalStatusOrActions() throws Exception {
        try (var mods = mockStatic(ModList.class)) {
            loaded(mods, false);
            var wheel = mock(TimeWheelCraftingCPU.class);
            assertTrue(ECOAe2LtCpuStatus.items(wheel).isEmpty());
            assertNull(ECOAe2LtCpuStatus.summary(wheel));
            toggle(wheel);
            verifyNoInteractions(wheel);
            loaded(mods, true);
            var unknown = mock(ICraftingCPU.class);
            assertTrue(ECOAe2LtCpuStatus.items(unknown).isEmpty());
            assertNull(ECOAe2LtCpuStatus.summary(unknown));
            toggle(unknown);
            verifyNoInteractions(unknown);
        }
    }

    @Test void ordinaryTaskIdentityTracksTheCurrentLink() {
        try (var mods = mockStatic(ModList.class)) {
            loaded(mods, true);
            var cpu = mock(TimeWheelCraftingCPU.class);
            var logic = mock(Ae2LtTimeWheelCraftingCpuLogic.class);
            var link = mock(ICraftingLink.class);
            when(cpu.getCraftingLogic()).thenReturn(logic);
            when(logic.hasJob()).thenReturn(true);
            when(logic.getLastLink()).thenReturn(link);
            UUID first = UUID.randomUUID(), next = UUID.randomUUID();
            when(link.getCraftingID()).thenReturn(first, next);
            assertEquals(first, ECOAe2LtCpuStatus.summary(cpu).jobId());
            assertEquals(next, ECOAe2LtCpuStatus.summary(cpu).jobId());
            when(logic.hasJob()).thenReturn(false);
            assertNull(ECOAe2LtCpuStatus.summary(cpu).jobId());
        }
    }

    private static void loaded(org.mockito.MockedStatic<ModList> mods, boolean loaded) {
        var list = mock(ModList.class);
        when(list.isLoaded("ae2lt")).thenReturn(loaded);
        mods.when(ModList::get).thenReturn(list);
    }

    private static void toggle(ICraftingCPU cpu) throws Exception {
        var method = ECOCraftingStatusUI.class.getDeclaredMethod("toggleSuspended", ICraftingCPU.class);
        method.setAccessible(true);
        method.invoke(null, cpu);
    }

    private static void assertAmount(CompoundTag tag, String page, String field, BigInteger expected) {
        assertTrue(tag.getList(page, Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
            .anyMatch(row -> new BigInteger(row.getByteArray(field)).equals(expected)), page + ": " + field);
    }

    @Test void queuedSelectionsRefreshOnceAndAcknowledgeTheLatestClickOrRemovedCpu() throws Exception {
        try (var mods = mockStatic(ModList.class)) {
            loaded(mods, false);
            var first = mock(ICraftingCPU.class);
            var second = mock(ICraftingCPU.class);
            when(first.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
            when(second.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
            var fixture = new Fixture(first, second);
            assertEquals(1, fixture.snapshot().getInt("selectedSerial"));
            int reads = fixture.cpuReads;
            for (int sequence = 1; sequence <= 20; sequence++) {
                fixture.select(sequence, sequence % 2 == 0 ? 2 : 1);
            }
            assertEquals(reads, fixture.cpuReads, "queued clicks must not each enumerate and sort the CPU list");
            var latest = fixture.snapshot();
            assertEquals(reads + 1, fixture.cpuReads);
            assertEquals(20, latest.getLong("selectionSequence"));
            assertEquals(2, latest.getInt("selectedSerial"));
            fixture.select(19, 1);
            fixture.select(20, 2);
            assertSame(latest, fixture.snapshot(), "duplicate and stale requests must not invalidate the cache");

            fixture.select(21, 2);
            fixture.cpus.remove(second);
            var removed = fixture.snapshot();
            assertEquals(21, removed.getLong("selectionSequence"));
            assertEquals(1, removed.getInt("selectedSerial"), "acknowledge the fallback if the clicked CPU disappeared");
        }
    }

    private static final class Fixture {
        final AtomicLong tick = new AtomicLong();
        final java.util.List<ICraftingCPU> cpus;
        int cpuReads;
        final Object state;
        Fixture(ICraftingCPU... cpus) throws Exception {
            this.cpus = new java.util.ArrayList<>(List.of(cpus));
            var source = new ComputationCpuStatusPanel.Source(null, tick::get, () -> true, () -> {
                cpuReads++;
                return this.cpus;
            },
                ignored -> {}, ICraftingCPU::cancelJob, () -> {});
            var type = Class.forName(ComputationCpuStatusPanel.class.getName() + "$ServerState");
            var constructor = type.getDeclaredConstructor(ComputationCpuStatusPanel.Source.class, CpuSelectionState.Identities.class);
            constructor.setAccessible(true);
            state = constructor.newInstance(source, new CpuSelectionState.Identities<ICraftingCPU>());
        }
        void select(long sequence, int serial) throws Exception {
            var request = new CompoundTag();
            request.putLong("sequence", sequence);
            request.putInt("serial", serial);
            var method = state.getClass().getDeclaredMethod("select", CompoundTag.class);
            method.setAccessible(true);
            method.invoke(state, request);
        }
        CompoundTag snapshot() throws Exception {
            var method = state.getClass().getDeclaredMethod("snapshot");
            method.setAccessible(true);
            try (var keys = mockStatic(AEKeyTypesInternal.class)) {
                keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
                return (CompoundTag) method.invoke(state);
            }
        }
    }

    private static final class ExactEntry extends CraftingStatusEntry implements BigStatusEntry {
        private Amounts amounts;
        ExactEntry(appeng.api.stacks.AEKey key, Amounts amounts) {
            super(0, key, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
            this.amounts = amounts;
        }
        @Override public Amounts ae2lt$amounts() { return amounts; }
        @Override public void ae2lt$amounts(Amounts value) { amounts = value; }
    }
}
