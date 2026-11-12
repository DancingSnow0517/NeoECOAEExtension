package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.CraftingServiceMixin;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.common.collect.ImmutableSet;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.mockito.MockedStatic;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCpuInsertHotPathTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private MockedStatic<AEKeyTypes> keyTypes;

    @BeforeEach void initializeKeyTypes() {
        keyTypes = mockStatic(AEKeyTypes.class);
        var types = Set.of(AEKeyType.items(), AEKeyType.fluids());
        keyTypes.when(AEKeyTypes::getAll).thenReturn(types);
        for (var type : types) keyTypes.when(() -> AEKeyTypes.get(type.getId())).thenReturn(type);
    }

    @AfterEach void closeKeyTypes() { keyTypes.close(); }

    @Test void networkWithoutEcoClustersDoesNotCreateAnEmptyIterator() throws Exception {
        var clusters = spy(new HashSet<NEComputationCluster>());
        assertEquals(0, route(clusters, mock(AEKey.class), 10, 0, Actionable.MODULATE));
        verify(clusters, never()).iterator();
    }

    @Test void vanillaAlreadyAcceptedEverythingDoesNotVisitEcoCpus() throws Exception {
        var cluster = mock(NEComputationCluster.class);
        assertEquals(10, route(Set.of(cluster), mock(AEKey.class), 10, 10, Actionable.MODULATE));
        verifyNoInteractions(cluster);
    }

    @Test void onlyTheRemainderIsOfferedAndTraversalStopsWhenFullyAccepted() throws Exception {
        AEKey key = mock(AEKey.class);
        for (Actionable mode : Actionable.values()) {
            var first = cpu(key, 6, 3, mode);
            var second = cpu(key, 3, 3, mode);
            var unused = mock(ECOCraftingCPU.class);
            assertEquals(10, route(Set.of(cluster(first, second, unused)), key, 10, 4, mode));
            verify(first.getLogic()).insert(key, 6, mode);
            verify(second.getLogic()).insert(key, 3, mode);
            verifyNoInteractions(unused);
        }
    }

    @Test void partialAcceptanceLeavesTheRemainderForNetworkStorage() throws Exception {
        AEKey key = mock(AEKey.class);
        var first = cpu(key, 8, 3, Actionable.MODULATE);
        var next = cpu(key, 5, 0, Actionable.MODULATE);
        assertEquals(5, route(new LinkedHashSet<>(List.of(cluster(first), cluster(next))),
            key, 10, 2, Actionable.MODULATE));
        verify(next.getLogic()).insert(key, 5, Actionable.MODULATE);
    }

    @Test void indexedJobBypassesOtherCpusAndKeepsDirectedSurplusWithItsOwner() throws Exception {
        var fixture = new Fixture(true);
        var owner = fixture.addCpu(UUID.randomUUID(), 3);
        var other = fixture.addCpu(UUID.randomUUID(), 10);
        UUID jobId = owner.getLogic().getProcessingJobId();
        var refresh = CraftingServiceMixin.class.getDeclaredMethod("onUpdateCPUClusters", CallbackInfo.class);
        refresh.setAccessible(true);
        refresh.invoke(fixture.service, new CallbackInfo("updateCPUClusters", false));
        clearInvocations(fixture.cluster, fixture.grid);

        for (int i = 0; i < 32; i++) {
            assertEquals(7, fixture.service.neoecoae$insertIntoCpuForJob(jobId, fixture.key, 7, Actionable.SIMULATE));
        }
        assertEquals(3, owner.getLogic().getWaitingFor(fixture.key));
        assertTrue(owner.getLogic().getInventory().list.isEmpty());
        assertEquals(7, fixture.service.neoecoae$insertIntoCpuForJob(jobId, fixture.key, 7, Actionable.MODULATE));
        assertEquals(7, owner.getLogic().getInventory().list.get(fixture.key));
        assertEquals(0, owner.getLogic().getWaitingFor(fixture.key));
        assertEquals(10, other.getLogic().getWaitingFor(fixture.key));
        assertTrue(other.getLogic().getInventory().list.isEmpty());
        verify(fixture.cluster, never()).getAllocatedCPUs();
        verify(fixture.cluster, never()).getActiveCPUs();
        verify(fixture.grid, never()).getMachineClasses();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelled", "removed", "moved"})
    void cachedOwnerMustStillOwnTheJobAndBelongToThisNetwork(String change) throws Exception {
        var fixture = new Fixture(true);
        UUID jobId = UUID.randomUUID();
        var cpu = fixture.addCpu(jobId, 10);
        assertEquals(7, fixture.service.neoecoae$insertIntoCpuForJob(jobId, fixture.key, 7, Actionable.SIMULATE));
        switch (change) {
            case "cancelled" -> cpu.cancelJob();
            case "removed" -> fixture.cluster.deactivate(cpu.getPlan());
            case "moved" -> when(fixture.node.getGrid()).thenReturn(mock(IGrid.class));
            default -> throw new AssertionError(change);
        }
        assertEquals(0, fixture.service.neoecoae$insertIntoCpuForJob(jobId, fixture.key, 7, Actionable.MODULATE));
        assertTrue(cpu.getLogic().getInventory().list.isEmpty());
        if (!change.equals("cancelled")) assertEquals(10, cpu.getLogic().getWaitingFor(fixture.key));
    }

    @Test void replacementOfACachedCpuReceivesOutputOnlyOnTheNewInstance() throws Exception {
        var fixture = new Fixture(true);
        UUID jobId = UUID.randomUUID();
        var previous = fixture.addCpu(jobId, 10);
        assertEquals(7, fixture.service.neoecoae$insertIntoCpuForJob(jobId, fixture.key, 7, Actionable.SIMULATE));
        fixture.cluster.deactivate(previous.getPlan());
        var restored = fixture.addCpu(jobId, 10);

        assertEquals(7, fixture.service.neoecoae$insertIntoCpuForJob(jobId, fixture.key, 7, Actionable.MODULATE));
        assertEquals(7, restored.getLogic().getInventory().list.get(fixture.key));
        assertEquals(3, restored.getLogic().getWaitingFor(fixture.key));
        assertTrue(previous.getLogic().getInventory().list.isEmpty());
        assertEquals(10, previous.getLogic().getWaitingFor(fixture.key));
    }

    @Test void cacheMissBeforeRegistrationDoesNotBlockLiveExactOutput() throws Exception {
        var fixture = new Fixture(false);
        UUID jobId = UUID.randomUUID();
        assertEquals(0, fixture.service.neoecoae$insertIntoCpuForJob(jobId, fixture.key, 7, Actionable.MODULATE));
        var cpu = fixture.addCpu(jobId, 0);
        var job = new ExecutingCraftingJob(
            new cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan(cpu.getPlan(), BigInteger.ZERO),
            cpu.getLogic()::postChange, cpu.getLogic().getJob().link, null);
        var waiting = (ECOExactInventory) job.waitingFor;
        waiting.setEnabled(true);
        var amount = BigInteger.ONE.shiftLeft(80);
        waiting.restore(Map.of(fixture.key, amount));
        cpu.getLogic().setJobFromLifecycle(job);

        assertEquals(amount, fixture.service.neoecoae$insertExactIntoCpuForJob(
            jobId, fixture.key, amount, Actionable.SIMULATE));
        assertEquals(amount, waiting.amount(fixture.key));
        assertTrue(cpu.getLogic().getInventory().list.isEmpty());
        clearInvocations(fixture.cluster, fixture.grid);
        assertEquals(amount, fixture.service.neoecoae$insertExactIntoCpuForJob(
            jobId, fixture.key, amount, Actionable.MODULATE));
        assertEquals(amount, cpu.getLogic().exactInventory().amount(fixture.key));
        assertEquals(BigInteger.ZERO, waiting.amount(fixture.key));
        verify(fixture.cluster, never()).getAllocatedCPUs();
        verify(fixture.grid, never()).getMachineClasses();
    }

    @Test void allocatedSnapshotsStayStableAndRefreshAcrossPlanAndCpuChanges() throws Exception {
        var fixture = new Fixture(true);
        var empty = fixture.cluster.getAllocatedCPUs();
        var cpu = fixture.addCpu(UUID.randomUUID(), 10);
        var before = fixture.cluster.getAllocatedCPUs();
        assertEquals(List.of(cpu), before);
        assertSame(before, fixture.cluster.getAllocatedCPUs());
        assertTrue(empty.isEmpty());
        assertThrows(UnsupportedOperationException.class, before::clear);
        var next = fixture.plan(10);
        assertTrue(fixture.cluster.replaceBigOrderPlan(cpu, next));
        assertTrue(fixture.cluster.isCpuRegistered(cpu));
        fixture.cluster.deactivate(next);
        assertTrue(fixture.cluster.getAllocatedCPUs().isEmpty());
        assertEquals(List.of(cpu), before, "an in-flight output traversal must keep a stable snapshot");

        var inactive = fixture.addCpu(UUID.randomUUID(), 0);
        fixture.cluster.getAllocatedCPUs();
        inactive.getLogic().setJobFromLifecycle(null);
        assertTrue(fixture.cluster.getActiveCPUs().isEmpty(), "active state must not be frozen in the snapshot");
        fixture.cluster.pruneInactiveCPUs();
        assertTrue(fixture.cluster.getAllocatedCPUs().isEmpty());
    }

    @Test void actualAe2FinalLocalLoadRemainsBeforeReturnAndOtherModsLocalCapture() throws Exception {
        var node = new ClassNode();
        try (var stream = CraftingService.class.getResourceAsStream("/appeng/me/service/CraftingService.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        var method = node.methods.stream().filter(value -> value.name.equals("insertIntoCpus")).findFirst().orElseThrow();
        int inserted = method.localVariables.stream().filter(value -> value.name.equals("inserted"))
                .findFirst().orElseThrow().index;
        var loads = java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(value -> value instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.LLOAD
                        && variable.var == inserted).toList();
        assertEquals(3, loads.size(), "review the primitive local hook if AE2 changes its loop");
        var next = loads.get(2).getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        assertNotNull(next);
        assertEquals(Opcodes.LRETURN, next.getOpcode());
    }

    private static long route(Set<NEComputationCluster> clusters, AEKey key, long amount, long already,
            Actionable mode) throws Exception {
        var mixin = mock(CraftingServiceMixin.class, CALLS_REAL_METHODS);
        var field = CraftingServiceMixin.class.getDeclaredField("neoecoae$computationClusters");
        field.setAccessible(true);
        field.set(mixin, clusters);
        var hook = CraftingServiceMixin.class.getDeclaredMethod("onInsertIntoCpus", long.class, AEKey.class,
            long.class, Actionable.class);
        hook.setAccessible(true);
        return (long) hook.invoke(mixin, already, key, amount, mode);
    }

    private static NEComputationCluster cluster(ECOCraftingCPU... cpus) {
        var cluster = mock(NEComputationCluster.class);
        when(cluster.getAllocatedCPUs()).thenReturn(List.of(cpus));
        return cluster;
    }

    private static ECOCraftingCPU cpu(AEKey key, long offered, long accepted, Actionable mode) {
        var cpu = mock(ECOCraftingCPU.class);
        var logic = mock(ECOCraftingCPULogic.class);
        when(cpu.getLogic()).thenReturn(logic);
        when(logic.insert(key, offered, mode)).thenReturn(accepted);
        return cpu;
    }

    private static final class Fixture {
        final AEKey key = AEItemKey.of(Items.STONE);
        final IGrid grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        final IGridNode node = mock(IGridNode.class);
        final NEComputationCluster cluster = spy(new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO));
        final CraftingServiceMixin service = mock(CraftingServiceMixin.class,
            withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));

        Fixture(boolean cachedCluster) throws Exception {
            var host = mock(ECOComputationSystemBlockEntity.class);
            when(host.getGridNode()).thenReturn(node);
            when(host.getCluster()).thenReturn(cluster);
            when(node.getGrid()).thenReturn(grid);
            when(grid.getMachineClasses()).thenReturn(Set.of(ECOComputationSystemBlockEntity.class));
            when(grid.getMachines(ECOComputationSystemBlockEntity.class)).thenReturn(Set.of(host));
            when(grid.getCraftingService()).thenReturn(mock(CraftingService.class));
            doNothing().when(cluster).recalculateRemainingStorage();
            doReturn(IActionSource.empty()).when(cluster).getActionSource();
            setField(NEComputationCluster.class, cluster, "controller", host);
            setField(CraftingServiceMixin.class, service, "grid", grid);
            setField(CraftingServiceMixin.class, service, "neoecoae$computationClusters",
                new HashSet<>(cachedCluster ? Set.of(cluster) : Set.of()));
            when(service.getCpus()).thenReturn(ImmutableSet.of());
        }

        ECOCraftingCPU addCpu(UUID jobId, long waiting) {
            var plan = plan(waiting);
            var core = mock(ECOComputationThreadingCoreBlockEntity.class);
            when(core.getTier()).thenReturn(ECOTier.L4);
            var cpu = new ECOCraftingCPU(cluster, plan, core);
            var link = new CraftingLink(cn.dancingsnow.neoecoae.util.LegacyNbt.input(cn.dancingsnow.neoecoae.util.LegacyNbt.registries(), CraftingCpuHelper.generateLinkData(jobId, true, false)), cpu);
            cpu.getLogic().setJobFromLifecycle(new ExecutingCraftingJob(plan, cpu.getLogic()::postChange, link, null));
            cluster.pickup(plan, cpu);
            return cpu;
        }

        CraftingPlan plan(long waiting) {
            var emitted = new KeyCounter();
            emitted.add(key, waiting);
            return new CraftingPlan(new GenericStack(AEItemKey.of(Items.DIAMOND), 64), 0, false, false,
                new KeyCounter(), emitted, new KeyCounter(), Map.of());
        }
    }

    private static void setField(Class<?> type, Object owner, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
