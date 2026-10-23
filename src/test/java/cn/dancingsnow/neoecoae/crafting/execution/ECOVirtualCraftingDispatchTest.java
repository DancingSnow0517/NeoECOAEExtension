package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

/** Real capability caches and FX admission for a full eight-host high-energy network. */
class ECOVirtualCraftingDispatchTest {
    private MockedStatic<TickHandler> ticks;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            ECOTier.values();
        }
    }

    @BeforeEach void setupTicks() {
        var tickHandler = mock(TickHandler.class);
        when(tickHandler.getCurrentTick()).thenReturn(100L);
        ticks = mockStatic(TickHandler.class);
        ticks.when(TickHandler::instance).thenReturn(tickHandler);
    }

    @AfterEach void closeTicks() { ticks.close(); }

    @Test
    void enablingCoolingReplacesAnAlreadyCachedFiniteDispatchCapacity() throws Exception {
        var fixture = new Fixture(false);
        var host = JdkCompat.first(fixture.hosts);
        var worker = JdkCompat.first(fixture.workers);
        assertFalse(host.isFullVirtualCraftingMode());
        assertEquals(32_768, host.getThreadCountForWorker(worker));
        assertFalse(fixture.network.getTotalBatchCapacity().unlimited());

        fixture.network.setActiveCooling(true);

        assertTrue(host.getCapabilitySnapshot().virtualMode());
        assertTrue(host.isFullVirtualCraftingMode(), "Dispatch must agree with the GUI after enabling cooling");
        assertTrue(fixture.network.getTotalBatchCapacity().unlimited());
        assertEquals(Integer.MAX_VALUE, host.getThreadCountForWorker(worker));
        assertEquals(Integer.MAX_VALUE, host.getDispatchThreadCapacity());
    }

    @Test
    void disablingCoolingReplacesAnAlreadyCachedUnlimitedDispatchCapacity() throws Exception {
        var fixture = new Fixture(true);
        var host = JdkCompat.first(fixture.hosts);
        assertTrue(host.isFullVirtualCraftingMode());
        assertTrue(fixture.network.getTotalBatchCapacity().unlimited());

        fixture.network.setActiveCooling(false);

        assertFalse(host.getCapabilitySnapshot().virtualMode());
        assertFalse(host.isFullVirtualCraftingMode(), "Disabling cooling must revoke virtual admission immediately");
        assertEquals(32_768, host.getThreadCountForWorker(JdkCompat.first(fixture.workers)));
        assertEquals(2_883_584, host.getDispatchThreadCapacity());
    }

    @ParameterizedTest
    @CsvSource({"9216,false", "9216,true", "1000000,true", "2147483664,false", "2147483664,true"})
    void cpuDispatchesTheWholeTaskIntoOneVirtualFxBatch(long crafts, boolean ecoPlan) throws Exception {
        var fixture = new Fixture(true);
        var job = fixture.prepareJob(BigInteger.valueOf(crafts), ecoPlan, false);

        assertEquals((int) Math.min(Integer.MAX_VALUE, crafts), fixture.dispatch());

        fixture.assertAccepted(job, BigInteger.valueOf(crafts));
        verify(fixture.bus, times(1)).eco$prepareFastPath(any());
        var thread = fixture.activeThread();
        assertEquals(crafts, thread.getCraftCount());
        assertTrue(thread.createSnapshot().virtualBatch());
        assertEquals(1, thread.getFiniteBatchCraftCount());
        fixture.assertFinishesInOneTick(thread);
    }

    @Test
    void enablingCoolingAfterFiniteCapacityWasQueriedDispatchesTheWholeVirtualBatch() throws Exception {
        var fixture = new Fixture(false);
        assertEquals(32_768, JdkCompat.first(fixture.hosts).getThreadCountForWorker(JdkCompat.first(fixture.workers)));
        fixture.network.setActiveCooling(true);
        var count = BigInteger.valueOf(1_000_000L);
        var job = fixture.prepareJob(count, true, false);

        assertEquals(1_000_000, fixture.dispatch());

        fixture.assertAccepted(job, count);
        assertTrue(fixture.activeThread().createSnapshot().virtualBatch());
    }

    @Test
    void exactCpuDispatchDoesNotNarrowTheBatchToLongOrInt() throws Exception {
        var fixture = new Fixture(true);
        var count = BigInteger.ONE.shiftLeft(80);
        var job = fixture.prepareJob(count, false, true);

        assertEquals(Integer.MAX_VALUE, fixture.dispatch());

        fixture.assertAccepted(job, count);
        verify(fixture.bus, times(1)).eco$prepareExactFastPath(any(), eq(count));
        assertEquals(count, fixture.activeThread().getExactCraftCount());
        fixture.assertFinishesInOneTick(fixture.activeThread());
    }

    private static final class Fixture {
        final NECraftingNetworkCluster network = new NECraftingNetworkCluster();
        final List<ECOCraftingSystemBlockEntity> hosts = new ArrayList<>();
        final List<ECOCraftingWorkerBlockEntity> workers = new ArrayList<>();
        final Level level = mock(Level.class);
        final ECOCraftingPatternBusBlockEntity bus;
        final AECraftingPattern pattern = mock(AECraftingPattern.class);
        final AEItemKey input = AEItemKey.of(Items.IRON_INGOT);
        final AEItemKey output = AEItemKey.of(Items.DIAMOND);
        final IEnergyService energy = mock(IEnergyService.class);
        final CraftingService service = mock(CraftingService.class);
        ECOCraftingCPULogic logic;

        Fixture(boolean cooling) throws Exception {
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            var clusters = new ArrayList<NECraftingCluster>();
            for (int hostIndex = 0; hostIndex < 8; hostIndex++) {
                var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
                var host = mock(ECOCraftingSystemBlockEntity.class, CALLS_REAL_METHODS);
                setField(NEBlockEntity.class, host, "cluster", cluster);
                setField(ECOCraftingSystemBlockEntity.class, host, "overclocked", true);
                setField(ECOCraftingSystemBlockEntity.class, host, "activeCooling", cooling);
                doReturn(new BlockPos(hostIndex * 16, 0, 0)).when(host).getBlockPos();
                doReturn(ECOTier.L9).when(host).getTier();
                doReturn(11).when(host).getMaxBuildLength();
                doReturn(true).when(host).hasHighEnergyNetworkSwitch();
                doReturn(false).when(host).hasNormalNetworkSwitch();
                doReturn(14_080L).when(host).getLocalFtParallelCapacity();
                doReturn(9).when(host).getLocalCoolingMaxOverclock();
                doNothing().when(host).setChanged();
                doNothing().when(host).markForUpdate();
                cluster.addBlockEntity(host);
                hosts.add(host);
                for (int lane = 0; lane < 11; lane++) {
                    var worker = mock(ECOCraftingWorkerBlockEntity.class, CALLS_REAL_METHODS);
                    setField(NEBlockEntity.class, worker, "cluster", cluster);
                    setField(ECOCraftingWorkerBlockEntity.class, worker, "craftingThreads", new ArrayList<ECOCraftingThread>());
                    doReturn(new BlockPos(hostIndex * 16 + lane, 0, 0)).when(worker).getBlockPos();
                    doReturn(mock(IManagedGridNode.class)).when(worker).getMainNode();
                    doReturn(false).when(worker).isMonitor();
                    doNothing().when(worker).markForUpdate();
                    cluster.addBlockEntity(worker);
                    workers.add(worker);
                }
                cluster.setNetworkCluster(network);
                clusters.add(cluster);
            }
            network.configure(clusters);

            bus = mock(ECOCraftingPatternBusBlockEntity.class, CALLS_REAL_METHODS);
            setField(NEBlockEntity.class, bus, "cluster", JdkCompat.first(clusters));
            doReturn(level).when(bus).getLevel();
            var dispatcherType = Class.forName("cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusDispatcher");
            var constructor = dispatcherType.getDeclaredConstructor(ECOCraftingPatternBusBlockEntity.class);
            constructor.setAccessible(true);
            setField(ECOCraftingPatternBusBlockEntity.class, bus, "dispatcher", constructor.newInstance(bus));
        }

        ExecutingCraftingJob prepareJob(BigInteger count, boolean ecoPlan, boolean exact) {
            var slot = mock(IPatternDetails.IInput.class);
            when(slot.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(input, 1)});
            when(slot.getMultiplier()).thenReturn(1L);
            when(slot.isValid(eq(input), any())).thenReturn(true);
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {slot});
            when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 1)});
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));

            var oneInput = new KeyCounter();
            oneInput.add(input, 1);
            var oneOutput = new KeyCounter();
            oneOutput.add(output, 1);
            var execution = ECOExtractedPatternExecution.create(pattern, new KeyCounter[] {oneInput},
                oneOutput, new KeyCounter(), level);
            assertTrue(execution.canUseFastPath(), execution.fastPathReason());
            // The warm recipe proof is a world fixture. Credential checks, provider preparation,
            // material/energy transactions, CPU accounting and FX admission all run production code.
            network.getFastPathCache().putPositive(execution.key(), execution.expectedOutputs(),
                execution.expectedContainerItems(), execution.inputItems(), 100L);

            long projection = count.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            ICraftingPlan plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(output, projection));
            when(plan.patternTimes()).thenReturn(Map.of(pattern, projection));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            if (exact) plan = new ECOExactCraftingPlan(plan, BigInteger.ONE);
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(UUID.randomUUID());
            ExecutingCraftingJob job;
            try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, ecoPlan ? executionPlan(projection) : null,
                    ignored -> {}, link, null);
            }
            if (exact) job.tasks.get(pattern).setExact(count, count);
            var cpu = mock(ECOCraftingCPU.class);
            when(cpu.isActive()).thenReturn(true);
            when(cpu.getLevel()).thenReturn(level);
            logic = new ECOCraftingCPULogic(cpu);
            logic.setJobFromPersistence(job);
            if (exact) logic.exactInventory().restore(Map.of(input, count));
            else logic.getInventory().insert(input, projection, Actionable.MODULATE);
            when(service.getProviders(pattern)).thenReturn(List.of(bus));
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
            return job;
        }

        private ECOExecutionPlan executionPlan(long count) {
            var task = new ECOExecutionPlan.TaskSpec(0, mock(PlanIdentity.PatternIdentity.class), pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern), count, 0, ECOExecutionPlan.TaskKind.DAG);
            var phase = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.DAG,
                List.of(0), List.of(), List.of());
            return new ECOExecutionPlan(mock(PlanIdentity.Signature.class), ExecutionMode.PHASED_DAG,
                List.of(task), List.of(phase), new ECOExecutionSchedule(List.of()));
        }

        int dispatch() { return logic.executeNormalCrafting(1, service, energy, level); }

        void assertAccepted(ExecutingCraftingJob job, BigInteger count) throws Exception {
            assertFalse(job.suspended, job.permanentExecutionError);
            assertEquals(BigInteger.ZERO, job.tasks.get(pattern).remainingExact());
            assertEquals(BigInteger.ZERO, logic.exactInventory().amount(input));
            assertEquals(count, ECOExactInventory.amount(job.waitingFor, output));
            assertEquals(1, workers.stream().mapToInt(worker -> worker.getThreadSnapshots().size()).sum());
            verify(bus, never()).pushPattern(any(IPatternDetails.class), any(KeyCounter[].class));
            for (var worker : workers) verify(worker, never()).pushBatch(any());
        }

        ECOCraftingThread activeThread() throws Exception {
            var field = ECOCraftingWorkerBlockEntity.class.getDeclaredField("craftingThreads");
            field.setAccessible(true);
            for (var worker : workers) {
                @SuppressWarnings("unchecked")
                var threads = (List<ECOCraftingThread>) field.get(worker);
                for (var thread : threads) if (!thread.isFree()) return thread;
            }
            throw new AssertionError("No FX accepted the batch");
        }

        void assertFinishesInOneTick(ECOCraftingThread thread) {
            var host = JdkCompat.first(hosts);
            doReturn(ECOCraftingSystemBlockEntity.VirtualLaneStartResult.STARTED)
                .when(host).tryStartVirtualLaneTickWithResult();
            thread.tick(host, 0, 1, 1);
            assertEquals(100, thread.createSnapshot().progress());
            assertTrue(thread.createSnapshot().outputsReady());
        }
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
