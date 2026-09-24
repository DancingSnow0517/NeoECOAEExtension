package cn.dancingsnow.neoecoae.crafting.execution.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ECOExactVirtualLedgerTest {
    @Test
    void exactDrainRetainsOnlyUnacceptedWideOutputs() {
        AEKey input = new TestKey();
        AEKey output = new TestKey();
        BigInteger crafts = BigInteger.valueOf(Long.MAX_VALUE);
        BigInteger totalOutput = crafts.multiply(BigInteger.valueOf(5));
        var ledger = new ECOExactVirtualLedger(
                crafts,
                List.of(new GenericStack(input, 1)),
                List.of(new GenericStack(output, 2)),
                List.of(new GenericStack(output, 3)));
        AtomicInteger changed = new AtomicInteger();

        assertEquals(crafts, ledger.snapshot(false).get(input));
        assertEquals(totalOutput, ledger.snapshot(true).get(output));
        assertFalse(
                ledger.drainExact(true, (key, amount) -> crafts.multiply(BigInteger.TWO), changed::incrementAndGet));
        assertEquals(
                crafts.multiply(BigInteger.valueOf(3)), ledger.snapshot(true).get(output));
        assertEquals(1, changed.get());
        assertTrue(ledger.drainExact(true, (key, amount) -> amount, changed::incrementAndGet));
        assertTrue(ledger.snapshot(true).isEmpty());
        assertEquals(2, changed.get());
    }

    @Test
    void longDestinationReceivesBoundedWindowsAndCannotOveraccept() {
        AEKey output = new TestKey();
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        var ledger = new ECOExactVirtualLedger(total, List.of(), List.of(new GenericStack(output, 1)), List.of());
        AtomicInteger changed = new AtomicInteger();

        assertFalse(ledger.drain(
                true,
                (key, offered) -> {
                    assertEquals(Long.MAX_VALUE, offered);
                    return offered;
                },
                changed::incrementAndGet));
        assertEquals(BigInteger.ONE, ledger.snapshot(true).get(output));
        assertThrows(
                IllegalStateException.class,
                () -> ledger.drainExact(true, (key, offered) -> offered.add(BigInteger.ONE), changed::incrementAndGet));
        assertEquals(BigInteger.ONE, ledger.snapshot(true).get(output));
        assertEquals(1, changed.get());
    }

    @Test
    void ownedTotalsAreNotMultipliedAgain() {
        AEKey input = new TestKey();
        AEKey output = new TestKey();
        var ledger = ECOExactVirtualLedger.fromOwnedTotals(
                1_000_000L,
                List.of(new GenericStack(input, 1_000_000L)),
                List.of(new GenericStack(output, 2_000_000L)),
                List.of());

        assertEquals(BigInteger.valueOf(1_000_000L), ledger.crafts());
        assertEquals(BigInteger.valueOf(1_000_000L), ledger.snapshot(false).get(input));
        assertEquals(BigInteger.valueOf(2_000_000L), ledger.snapshot(true).get(output));
    }

    private static final class TestKey extends AEKey {
        @Override
        public appeng.api.stacks.AEKeyType getType() {
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
