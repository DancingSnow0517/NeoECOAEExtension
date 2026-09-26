package cn.dancingsnow.neoecoae.crafting.execution.batch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOBatchExecutorTest {
    interface FastProvider extends appeng.api.networking.crafting.ICraftingProvider,
            cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider {}
    final AEKey key = mock(AEKey.class, RETURNS_DEEP_STUBS);
    final IPatternDetails pattern = mock(IPatternDetails.class, RETURNS_DEEP_STUBS);
    final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
    final ECOBatchExecutor.LinearEnergy energy = mock(ECOBatchExecutor.LinearEnergy.class);
    final KeyCounter input = new KeyCounter();
    final Level level = mock(Level.class);

    ECOBatchExecutorTest() {
        input.add(key, 2);
        inventory.insert(key, 100, Actionable.MODULATE);
    }

    ECOBatchPlan plan(long copies) {
        return new ECOBatchPlanner().plan(new ECOBatchPlanRequest(ECOPatternIdentity.of(pattern, this),
                copies, 100, 100, 100, 100, 100, 100, 100, 100, ECOBatchMode.LINEAR));
    }

    ECOBatchAdmission execute(ECOBatchProvider provider) {
        return ECOBatchExecutor.execute(plan(10), new KeyCounter[]{input}, new KeyCounter(), new KeyCounter(),
                inventory, level, UUID.randomUUID(), () -> energy, provider);
    }

    @Test void partialAdmissionReturnsOnlyTheUnacceptedLinearSuffix() {
        var result = execute(batch -> {
            assertEquals(80, inventory.list.get(key));
            assertSame(pattern, batch.identity().originalPattern());
            assertEquals(20, batch.inputCounters()[0].get(key));
            // Providers may consume the counters; the resource receipt must be independent.
            batch.inputCounters()[0].clear();
            return ECOBatchAdmission.accepted(4, false);
        });
        assertEquals(4, result.acceptedCrafts());
        assertEquals(92, inventory.list.get(key));
        verify(energy).refundUnaccepted(4, 10);
        verify(energy, never()).refund();
    }

    @Test void rejectionRestoresMaterialsAndEnergy() {
        assertEquals(ECOBatchAdmission.Status.REJECTED, execute(batch -> ECOBatchAdmission.rejected()).status());
        assertEquals(100, inventory.list.get(key));
        verify(energy).refund();
    }

    @Test void uncertainOrInvalidAdmissionRetainsResourcesAndCannotFallBack() {
        for (var receipt : List.of(ECOBatchAdmission.indeterminate(), ECOBatchAdmission.accepted(11, false))) {
            inventory.list.set(key, 100);
            assertThrows(ECOIndeterminateBatchException.class, () -> execute(batch -> receipt));
            assertEquals(80, inventory.list.get(key));
        }
        verify(energy, times(2)).commit();
        verify(energy, never()).refund();
    }

    @Test void ordinaryProviderExceptionIsConservativelyRetained() {
        assertThrows(ECOIndeterminateBatchException.class, () -> execute(batch -> {
            throw new IllegalStateException("May have accepted inputs");
        }));
        assertEquals(80, inventory.list.get(key));
        verify(energy).commit();
    }

    @Test void disappearingInputsNeverReachTheProvider() {
        var plan = plan(10);
        inventory.list.set(key, 1);
        var provider = mock(ECOBatchProvider.class);
        var result = ECOBatchExecutor.execute(plan, new KeyCounter[]{input}, new KeyCounter(), new KeyCounter(),
                inventory, level, UUID.randomUUID(), () -> energy, provider);
        assertEquals(ECOBatchAdmission.Status.REJECTED, result.status());
        assertEquals(1, inventory.list.get(key));
        verifyNoInteractions(provider);
        verify(energy).refund();
    }

    @Test void overlappingSlotsAreDebitedAndRefundedExactlyOnce() {
        var result = ECOBatchExecutor.execute(plan(10), new KeyCounter[]{input, input}, new KeyCounter(),
                new KeyCounter(), inventory, level, UUID.randomUUID(), () -> energy,
                batch -> ECOBatchAdmission.accepted(3, false));
        assertEquals(3, result.acceptedCrafts());
        assertEquals(88, inventory.list.get(key));
    }

    @Test void materializedBatchCannotBeCommittedTwiceOrReplayedAfterRollback() {
        var materializer = new ECOBatchMaterializer();
        var batch = materializer.materialize(plan(10), new KeyCounter[]{input}, new KeyCounter(),
                new KeyCounter(), inventory, level);
        batch.rollback();
        batch.rollback();
        assertEquals(100, inventory.list.get(key));
        assertThrows(IllegalStateException.class, batch::commit);
        assertThrows(IllegalStateException.class, batch::inputCounters);
    }

    @Test void preparedStatefulTotalsAreNotMultipliedAndAtomicFailureRefunds() {
        var reservation = mock(ECOFastPathFacade.Reservation.class);
        assertFalse(ECOBatchExecutor.executePrepared(inventory, List.of(new GenericStack(key, 1)), Map.of(),
                reservation, () -> {
                    assertEquals(99, inventory.list.get(key));
                    return false;
                }));
        assertEquals(100, inventory.list.get(key));
        verify(reservation).refund();
    }

    @Test void statefulFacadeUsesTheVerifiedToolContractThroughTheNewExecutor() {
        var tool = mock(AEKey.class, RETURNS_DEEP_STUBS);
        inventory.insert(tool, 1, Actionable.MODULATE);
        var calculator = mock(cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator.class);
        when(calculator.arithmeticBatchLimit()).thenReturn(5L);
        when(calculator.prepareBatch(inventory, 5L)).thenReturn(
                new cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator.BatchContract(
                        5, List.of(new GenericStack(tool, 1), new GenericStack(key, 10)),
                        List.of(new GenericStack(tool, 1))));
        var provider = mock(FastProvider.class);
        when(provider.eco$prepareFastPath(any())).thenReturn(
                new cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider.Preparation(
                        5, calculator, true, batch -> {
                            assertEquals(5, batch.craftCount());
                            assertEquals(0, inventory.list.get(tool));
                            assertEquals(90, inventory.list.get(key));
                            assertEquals(List.of(new GenericStack(tool, 1)), batch.remainingTotal());
                            return true;
                        }));
        var toolCounter = new KeyCounter(); toolCounter.add(tool, 1);
        var batch = ECOFastPathFacade.prepare(provider, pattern, new KeyCounter[]{input, toolCounter},
                new KeyCounter(), toolCounter, inventory, 20, 0, null, level, UUID.randomUUID());
        assertNotNull(batch);
        var reservation = mock(ECOFastPathFacade.Reservation.class);
        assertTrue(batch.submit(ignored -> reservation));
        verify(reservation).commit();
        assertThrows(IllegalStateException.class, () -> batch.submit(ignored -> reservation));
    }

    @Test void exactDebitNeverNarrowsToLongAndIndeterminateCommitKeepsOwnership() {
        var exact = new ECOExactInventory(ignored -> {});
        exact.setEnabled(true);
        var huge = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TEN);
        exact.restore(Map.of(key, huge));
        var reservation = mock(ECOFastPathFacade.Reservation.class);
        assertThrows(ECOIndeterminateBatchException.class, () -> ECOBatchExecutor.executePrepared(exact,
                List.of(), Map.of(key, huge), reservation, () -> {
                    throw new ECOIndeterminateBatchException("Uncertain", null);
                }));
        assertEquals(BigInteger.ZERO, exact.amount(key));
        verify(reservation).commit();
        verify(reservation, never()).refund();
    }

    @Test void plannerCombinesEveryLimitAndSingleModeNeverBatches() {
        var identity = ECOPatternIdentity.of(pattern, this);
        var planner = new ECOBatchPlanner();
        assertEquals(2, planner.plan(new ECOBatchPlanRequest(identity,
                90, 80, 70, 60, 50, 40, 30, 20, 2, ECOBatchMode.LINEAR)).craftCount());
        assertEquals(1, planner.plan(new ECOBatchPlanRequest(identity,
                90, 80, 70, 60, 50, 40, 30, 20, 2, ECOBatchMode.SINGLE)).craftCount());
        assertNull(planner.plan(new ECOBatchPlanRequest(identity,
                90, 80, 70, 60, 0, 40, 30, 20, 2, ECOBatchMode.LINEAR)));
        var huge = BigInteger.TEN.pow(30);
        assertEquals(huge, planner.planExact(huge, huge.add(BigInteger.ONE), huge, huge));
    }
}
