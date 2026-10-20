package cn.dancingsnow.neoecoae.compat.useless;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ECOUselessBigIntegerDispatchTest {
    private final AEKey input = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final AEKey output = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final IPatternDetails pattern = mock(IPatternDetails.class, RETURNS_DEEP_STUBS);
    private final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
    private final ECOFastPathFacade.Reservation energy = mock(ECOFastPathFacade.Reservation.class);
    private final Target target = new Target();

    private ECOFastPathFacade.PreparedBatch prepare(long wanted, long stock) throws Exception {
        var api = ECOUselessBatchProviderBridge.BigIntegerReflectionApi.resolve(
            NativeProvider.class, Target.class, Capacity.class, Ticket.class, Binding.class, Patterns.class);
        var adapter = new ECOUselessBatchProviderBridge.BigIntegerAdapter(api, target);
        var provider = mock(CpuProvider.class);
        when(provider.eco$prepareFastPath(any())).thenAnswer(call -> adapter.eco$prepareFastPath(call.getArgument(0)));
        var inputs = new KeyCounter();
        inputs.add(input, 2);
        var outputs = new KeyCounter();
        outputs.add(output, 3);
        inventory.list.set(input, stock);
        return ECOFastPathFacade.prepare(provider, pattern, new KeyCounter[]{inputs}, outputs,
            new KeyCounter(), inventory, wanted, 0, null, null, null);
    }

    @Test void exactNineToOneUsesNativeReceiptAndOrdinaryDispatchRemainsLongBounded() throws Exception {
        var api = ECOUselessBatchProviderBridge.BigIntegerReflectionApi.resolve(
            NativeProvider.class, Target.class, Capacity.class, Ticket.class, Binding.class, Patterns.class);
        var adapter = new ECOUselessBatchProviderBridge.BigIntegerAdapter(api, target);
        var provider = mock(CpuProvider.class);
        when(provider.eco$prepareFastPath(any())).thenAnswer(call -> adapter.eco$prepareFastPath(call.getArgument(0)));
        var exact = new cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory(ignored -> {});
        exact.setEnabled(true);
        for (int i = 0; i < 9; i++) exact.insert(input, Long.MAX_VALUE, appeng.api.config.Actionable.MODULATE);
        var inputs = new KeyCounter(); inputs.add(input, 9);
        var outputs = new KeyCounter(); outputs.add(output, 1);
        // Even an inventory retaining a cancelled big order must not opt ordinary dispatch into bigint.
        var ordinary = ECOFastPathFacade.prepare(provider, pattern, new KeyCounter[]{inputs}, outputs,
            new KeyCounter(), exact, Long.MAX_VALUE, 0, null, null, null);
        assertEquals(Long.MAX_VALUE / 9, ordinary.craftCount());
        var batch = ECOFastPathFacade.prepare(provider, pattern, new KeyCounter[]{inputs}, outputs,
            new KeyCounter(), exact, Long.MAX_VALUE, 0, null, null, null, true);
        assertNotNull(batch);
        assertEquals(Long.MAX_VALUE, batch.craftCount());
        assertTrue(batch.submit(ignored -> energy));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), target.requested);
        assertEquals(9, target.prototypeAmount);
        assertSame(target.admittedPrototype, target.committedPrototype);
        assertEquals(BigInteger.ZERO, exact.amount(input));
    }

    @Test void trillionCopyBatchDebitsAllInputsAndCommitsOneUnscaledPrototype() throws Exception {
        long copies = 1_000_000_000_000L;
        var batch = prepare(copies, copies * 2 + 14);
        assertNotNull(batch);
        assertEquals(copies, batch.craftCount());
        assertEquals(copies * 3, batch.outputs().getFirst().amount());
        assertTrue(batch.submit(ignored -> energy));
        assertEquals(BigInteger.valueOf(copies), target.requested);
        assertSame(target.admittedPrototype, target.committedPrototype);
        assertEquals(2, target.prototypeAmount);
        assertEquals(14, inventory.list.get(input));
        assertEquals(1, target.commits);
        verify(energy).commit();
        assertThrows(IllegalStateException.class, () -> batch.submit(ignored -> energy));
        assertEquals(14, inventory.list.get(input));
    }

    @Test void exactDispatchExceedsLongForCopiesInputsAndOutputsAndRollsBackRejection() throws Exception {
        var api = ECOUselessBatchProviderBridge.BigIntegerReflectionApi.resolve(
            NativeProvider.class, Target.class, Capacity.class, Ticket.class, Binding.class, Patterns.class);
        var adapter = new ECOUselessBatchProviderBridge.BigIntegerAdapter(api, target);
        var copies = BigInteger.TEN.pow(24).add(BigInteger.valueOf(17));
        var exact = new cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory(ignored -> {});
        exact.setEnabled(true);
        exact.restore(java.util.Map.of(input, copies.multiply(BigInteger.valueOf(9))));
        var context = new cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext(pattern,
            java.util.List.of(java.util.List.of(new appeng.api.stacks.GenericStack(input, 9))),
            java.util.List.of(new appeng.api.stacks.GenericStack(output, 4)), java.util.List.of(), null, null);
        var rejected = cn.dancingsnow.neoecoae.crafting.execution.batch.ECOExactBatchPlanner.prepare(
            adapter, context, exact, copies, java.util.Map.of());
        target.reject = true;
        assertFalse(rejected.submit(energy));
        assertEquals(copies.multiply(BigInteger.valueOf(9)), exact.amount(input));
        target.reject = false;
        var accepted = cn.dancingsnow.neoecoae.crafting.execution.batch.ECOExactBatchPlanner.prepare(
            adapter, context, exact, copies, java.util.Map.of());
        assertEquals(copies, accepted.craftCount());
        assertEquals(copies.multiply(BigInteger.valueOf(4)), accepted.outputs().get(output));
        assertTrue(accepted.submit(energy));
        assertEquals(copies, target.requested);
        assertEquals(9, target.prototypeAmount);
        assertEquals(BigInteger.ZERO, exact.amount(input));
        assertThrows(IllegalStateException.class, () -> accepted.submit(energy));
    }

    @Test void liveCapacityAndInventoryStillBoundTheBatch() throws Exception {
        target.capacity = BigInteger.valueOf(17);
        assertEquals(17, prepare(100, 200).craftCount());
        assertEquals(9, prepare(100, 18).craftCount());
    }

    @Test void capacityBeyondLongIsBoundedBeforeMultiplyingOutputs() throws Exception {
        var batch = prepare(Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE / 3, batch.craftCount());
        assertTrue(batch.submit(ignored -> energy));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE / 3), target.requested);
    }

    @Test void shrinkingAdmissionDoesNotCommitOrMiscountTheBatch() throws Exception {
        var batch = prepare(100, 200);
        target.shrink = true;
        assertFalse(batch.submit(ignored -> energy));
        assertEquals(0, target.commits);
        assertEquals(200, inventory.list.get(input));
        verify(energy).refund();
    }

    @Test void explicitRejectionRestoresTheWholeBatch() throws Exception {
        var batch = prepare(100, 200);
        target.reject = true;
        assertFalse(batch.submit(ignored -> energy));
        assertEquals(200, inventory.list.get(input));
        assertEquals(1, target.commits);
        verify(energy).refund();
    }

    @Test void exceptionAfterCommitStartsRetainsInputsAndEnergy() throws Exception {
        var batch = prepare(100, 200);
        target.throwOnCommit = true;
        assertThrows(ECOIndeterminateBatchException.class, () -> batch.submit(ignored -> energy));
        assertEquals(0, inventory.list.get(input));
        verify(energy).commit();
        verify(energy, never()).refund();
    }

    @Test void zeroCapacityDoesNotCreateADispatch() throws Exception {
        target.capacity = BigInteger.ZERO;
        assertNull(prepare(100, 200));
        assertEquals(200, inventory.list.get(input));
    }

    @Test void scaledPatternsDoNotEnterTheUnwrappedApi() throws Exception {
        var wrapper = mock(WrappedPattern.class);
        when(wrapper.original()).thenReturn(pattern);
        var api = ECOUselessBatchProviderBridge.BigIntegerReflectionApi.resolve(
            NativeProvider.class, Target.class, Capacity.class, Ticket.class, Binding.class, Patterns.class);
        var context = mock(cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext.class);
        when(context.pattern()).thenReturn(wrapper);
        assertNull(new ECOUselessBatchProviderBridge.BigIntegerAdapter(api, target).eco$prepareFastPath(context));
    }

    interface CpuProvider extends ICraftingProvider, ECOFastPathDispatchProvider {}
    public interface NativeProvider { Target bigIntegerTarget(); }
    public interface WrappedPattern extends IPatternDetails { IPatternDetails original(); }
    public static final class Binding {}
    public record Capacity(BigInteger accepted) {}
    public static final class Patterns {
        public static IPatternDetails unwrap(IPatternDetails pattern) {
            return pattern instanceof WrappedPattern wrapped ? wrapped.original() : pattern;
        }
    }

    // Independent stand-in for the upstream public contract. The private Ticket class is
    // invoked through its public interface, as with Useless's actual admission object.
    public interface Ticket {
        BigInteger count();
        boolean commit(KeyCounter[] prototype);
    }
    public static final class Target {
        BigInteger capacity = BigInteger.TEN.pow(25);
        BigInteger requested;
        KeyCounter[] admittedPrototype;
        KeyCounter[] committedPrototype;
        long prototypeAmount;
        int commits;
        boolean shrink;
        boolean reject;
        boolean throwOnCommit;

        public Capacity capacity(IPatternDetails pattern, KeyCounter[] prototype, BigInteger requested) {
            return new Capacity(capacity.min(requested));
        }

        public Ticket admit(IPatternDetails pattern, KeyCounter[] prototype, BigInteger count, Binding binding) {
            requested = count;
            admittedPrototype = prototype;
            return new Ticket() {
                public BigInteger count() { return shrink ? count.subtract(BigInteger.ONE) : count; }
                public boolean commit(KeyCounter[] receipt) {
                    assertSame(prototype, receipt);
                    committedPrototype = receipt;
                    commits++;
                    prototypeAmount = receipt[0].iterator().next().getLongValue();
                    if (throwOnCommit) throw new IllegalStateException("acceptance uncertain");
                    if (reject) return false;
                    for (var slot : receipt) slot.clear();
                    return true;
                }
            };
        }
    }
}
