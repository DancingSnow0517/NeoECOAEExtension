package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.api.me.provider.*;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.*;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtCraftingAdapter;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.*;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.CraftingServiceMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.common.collect.ImmutableSet;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.crafting.big.*;
import com.moakiee.thunderbolt.compat.neoeco.NeoEcoFastPathCompat;
import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exact Tianshu escrow -> public ECO admission -> real FX thread -> exact directed receipt. */
class ECOAe2LtBigCraftingInteropTest {
    private static final BigInteger COUNT = BigInteger.TEN.pow(28).add(BigInteger.valueOf(17));

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void verifiedFxLaneAcceptsAndReturnsMoreThanLongWithoutPerWindowLoops() throws Exception {
        var f = fixture();
        var worker = mock(ECOCraftingWorkerBlockEntity.class, RETURNS_DEEP_STUBS);
        var controller = mock(ECOCraftingSystemBlockEntity.class);
        var cache = mock(ECOCraftingFastPathCache.class);
        var verified = mock(ECOVerifiedFastPathRecipe.class);
        when(worker.getFastPathCache()).thenReturn(cache);
        when(worker.isControlledBy(controller)).thenReturn(true);
        when(controller.isFullVirtualCraftingMode()).thenReturn(true);
        when(verified.isIssuedBy(cache)).thenReturn(true);
        when(verified.isCurrent(anyLong())).thenReturn(true);
        var inputs = List.of(new GenericStack(f.input, 2));
        var outputs = List.of(new GenericStack(f.output, 3));
        when(verified.inputsPerCraft()).thenReturn(inputs);
        when(verified.outputsPerCraft()).thenReturn(outputs);
        when(verified.remainingPerCraft()).thenReturn(List.of());
        when(verified.withVirtualBatch(1, f.job.id)).thenReturn(
                new ECOVerifiedVirtualExecution(verified, 1, f.job.id, inputs, outputs, List.of()));
        var thread = new ECOCraftingThread(worker);
        when(f.provider.eco$prepareExactFastPath(any(), any())).thenAnswer(call -> {
            ECOBatchDispatchContext context = call.getArgument(0);
            BigInteger offered = call.getArgument(1);
            assertEquals(f.job.id, context.craftingJobId());
            return new ECOFastPathDispatchProvider.ExactPreparation(offered,
                    () -> thread.pushExactVirtualBatch(verified, offered, context.craftingJobId(), controller));
        });
        assertTrue(f.dispatch());
        assertEquals(COUNT, f.job.acceptedCopies);
        assertEquals(COUNT, thread.getExactCraftCount());
        assertEquals(BigInteger.ZERO, f.job.escrow.getOrDefault(f.input, BigInteger.ZERO));
        assertEquals(COUNT.multiply(BigInteger.valueOf(3)), f.job.waiting.get(f.output));
        verify(f.provider, never()).pushPattern(any(), any());

        var host = mock(TianshuSupercomputerControllerBlockEntity.class);
        var service = new BigCraftingService(host);
        field(BigCraftingService.class, "job").set(service, f.job);
        var cpu = service.cpu();
        var total = COUNT.multiply(BigInteger.valueOf(3));
        assertEquals(BigInteger.ZERO, ECOAe2LtCraftingAdapter.insertExactCpuOutput(
                cpu, UUID.randomUUID(), f.output, total, Actionable.MODULATE));
        assertEquals(total, ECOAe2LtCraftingAdapter.insertExactCpuOutput(cpu, f.job.id,
                f.output, total, Actionable.SIMULATE));
        assertEquals(total, f.job.waiting.get(f.output));

        var router = mock(CraftingServiceMixin.class, CALLS_REAL_METHODS);
        when(router.getCpus()).thenReturn(ImmutableSet.of(cpu));
        var crafting = mock(CraftingService.class, withSettings().extraInterfaces(ECOCraftingOutputRouter.class));
        var outputRouter = (ECOCraftingOutputRouter) crafting;
        when(outputRouter.neoecoae$insertExactIntoCpuForJob(eq(f.job.id), eq(f.output), any(), any()))
                .thenAnswer(call -> router.neoecoae$insertExactIntoCpuForJob(call.getArgument(0),
                        call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(grid.getCraftingService()).thenReturn(crafting);
        when(worker.getMainNode().getGrid()).thenReturn(grid);
        var eject = ECOCraftingThread.class.getDeclaredMethod("ejectOutputs");
        eject.setAccessible(true);
        assertEquals(true, eject.invoke(thread));
        assertTrue(thread.isFree());
        assertTrue(f.job.waiting.isEmpty());
        assertEquals(total, f.job.escrow.get(f.output));
        verify(outputRouter, times(1)).neoecoae$insertExactIntoCpuForJob(f.job.id, f.output, total, Actionable.MODULATE);
        verify(outputRouter, never()).neoecoae$insertIntoCpuForJob(any(), any(), anyLong(), any());
    }

    @Test void rejectedExactFxBatchRestoresEveryInputAndWaitingAmount() throws Exception {
        var f = fixture();
        when(f.provider.eco$prepareExactFastPath(any(), any())).thenAnswer(call ->
                new ECOFastPathDispatchProvider.ExactPreparation(call.getArgument(1), () -> false));
        assertFalse(f.dispatch());
        assertEquals(COUNT.multiply(BigInteger.TWO), f.job.escrow.get(f.input));
        assertTrue(f.job.waiting.isEmpty());
        assertEquals(BigInteger.ZERO, f.job.acceptedCopies);
        verify(f.energy, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
    }

    @Test void indeterminateExactFxCommitStopsTheJobWithoutRefundingOrRetrying() throws Exception {
        var f = fixture();
        when(f.provider.eco$prepareExactFastPath(any(), any())).thenAnswer(call ->
                new ECOFastPathDispatchProvider.ExactPreparation(call.getArgument(1), () -> {
                    throw new ECOIndeterminateBatchException("accepted before failure", null);
                }));
        assertFalse(f.dispatch());
        assertTrue(f.job.dispatchFailed);
        assertEquals(BigInteger.ZERO, f.job.escrow.getOrDefault(f.input, BigInteger.ZERO));
        assertEquals(COUNT.multiply(BigInteger.valueOf(3)), f.job.waiting.get(f.output));
        assertFalse(f.dispatch());
        verify(f.provider, times(1)).eco$prepareExactFastPath(any(), any());
    }

    @Test void unknownExternalProviderKeepsTheLongContractAndGetsNoExactCommit() {
        var provider = mock(ICraftingProvider.class);
        assertNull(NeoEcoFastPathCompat.prepareExact(provider, mock(IPatternDetails.class),
                new KeyCounter[0], COUNT, null, UUID.randomUUID()));
        verify(provider, never()).pushPattern(any(), any());
    }

    @Test void boundedFxAndPowerLimitedBatchesKeepTheExistingLongLane() throws Exception {
        var small = fixture(BigInteger.valueOf(100));
        assertFalse(small.dispatch());
        verify(small.provider, never()).eco$prepareExactFastPath(any(), any());
        var limited = fixture();
        when(limited.energy.extractAEPower(anyDouble(), any(), any())).thenReturn(8.0);
        assertFalse(limited.dispatch());
        verify(limited.provider, never()).eco$prepareExactFastPath(any(), any());
        assertEquals(COUNT.multiply(BigInteger.TWO), limited.job.escrow.get(limited.input));
    }

    @Test void aLongCraftCountStillUsesExactTransportWhenItsMaterialTotalsOverflow() throws Exception {
        var count = BigInteger.valueOf(Long.MAX_VALUE).divide(BigInteger.TWO).add(BigInteger.ONE);
        var f = fixture(count);
        when(f.provider.eco$prepareExactFastPath(any(), any())).thenAnswer(call ->
                new ECOFastPathDispatchProvider.ExactPreparation(call.getArgument(1), () -> true));
        assertTrue(f.dispatch());
        assertEquals(count, f.job.acceptedCopies);
        assertEquals(count.multiply(BigInteger.valueOf(3)), f.job.waiting.get(f.output));
        assertEquals(BigInteger.ZERO, f.job.escrow.getOrDefault(f.input, BigInteger.ZERO));
    }

    @Test void exactProviderCapacityLeavesTheUnacceptedCopiesInCpuEscrow() throws Exception {
        var f = fixture();
        var accepted = COUNT.subtract(BigInteger.valueOf(7));
        when(f.provider.eco$prepareExactFastPath(any(), any())).thenReturn(
                new ECOFastPathDispatchProvider.ExactPreparation(accepted, () -> true));
        assertTrue(f.dispatch());
        assertEquals(accepted, f.job.acceptedCopies);
        assertEquals(BigInteger.valueOf(14), f.job.escrow.get(f.input));
        assertEquals(accepted.multiply(BigInteger.valueOf(3)), f.job.waiting.get(f.output));
    }

    @Test void bigCpuCancellationPublishesTheSameWorkerRecoveryNotice() throws Exception {
        var f = fixture();
        var service = new BigCraftingService(mock(TianshuSupercomputerControllerBlockEntity.class));
        field(BigCraftingService.class, "job").set(service, f.job);
        try (var lifecycle = mockStatic(cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle.class)) {
            service.cancel();
            lifecycle.verify(() -> cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle
                    .cancelAndRecover(null, f.job.id));
        }
        assertTrue(f.job.cancelled);
    }

    @Test void oldCpuRouterReceivesOnlyOneSafeLongWindow() {
        var output = AEItemKey.of(net.minecraft.world.item.Items.DIAMOND);
        var job = UUID.randomUUID();
        var router = mock(ECOCraftingOutputRouter.class, CALLS_REAL_METHODS);
        when(router.neoecoae$insertIntoCpuForJob(job, output, Long.MAX_VALUE, Actionable.MODULATE))
                .thenReturn(Long.MAX_VALUE);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE),
                router.neoecoae$insertExactIntoCpuForJob(job, output, COUNT, Actionable.MODULATE));
    }

    private interface Provider extends ICraftingProvider, ECOFastPathDispatchProvider {}

    private static Fixture fixture() {
        return fixture(COUNT);
    }

    private static Fixture fixture(BigInteger count) {
        var input = AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT);
        var output = AEItemKey.of(net.minecraft.world.item.Items.DIAMOND);
        var pattern = mock(IPatternDetails.class);
        var slot = mock(IPatternDetails.IInput.class);
        when(slot.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(input, 2)});
        when(slot.getMultiplier()).thenReturn(1L);
        when(slot.isValid(eq(input), any())).thenReturn(true);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(net.minecraft.world.item.Items.PAPER));
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {slot});
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 3)});
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(output, 3));
        var recipe = BigProviderRecipe.capture(pattern, List.of(Map.of(input, 2L)), null);
        assertNotNull(recipe);
        var program = BigExecutionProgram.of(List.of(BigExecutionProgram.block(
                List.of(new BigExecutionProgram.Step<>(recipe.pattern(), count)), BigInteger.ONE)));
        var job = new BigCraftingJob(output, count.multiply(BigInteger.valueOf(3)), program);
        job.escrow.put(input, count.multiply(BigInteger.TWO));
        job.beginStreaming();
        var provider = mock(Provider.class);
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
        return new Fixture(job, recipe, pattern, provider, energy, input, output);
    }

    private record Fixture(BigCraftingJob job, BigProviderRecipe recipe, IPatternDetails pattern,
            Provider provider, IEnergyService energy, AEItemKey input, AEItemKey output) {
        boolean dispatch() throws Exception {
            var execution = mock(ECOExtractedPatternExecution.class);
            when(execution.canUseFastPath()).thenReturn(true);
            when(execution.fastPathType()).thenReturn(ECORecipeClassifier.Type.NORMAL);
            var outputs = pattern.getOutputs();
            when(execution.expectedOutputs()).thenReturn(outputs);
            try (var proof = mockStatic(ECOExtractedPatternExecution.class);
                 var log = mockStatic(appeng.core.AELog.class)) {
                proof.when(() -> ECOExtractedPatternExecution.fromProviderPush(eq(pattern), any(), isNull()))
                        .thenReturn(execution);
                var dispatch = Class.forName("com.moakiee.ae2lt.crafting.big.NeoEcoBigIntegerCompat")
                        .getDeclaredMethod("dispatch", BigCraftingJob.class, BigProviderRecipe.class,
                                IPatternDetails.class, ICraftingProvider.class, net.minecraft.world.level.Level.class,
                                IEnergyService.class, Runnable.class);
                dispatch.setAccessible(true);
                try { return (boolean) dispatch.invoke(null, job, recipe, pattern, provider, null, energy, (Runnable) () -> {}); }
                catch (InvocationTargetException failed) { throw new AssertionError(failed.getCause()); }
            }
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
