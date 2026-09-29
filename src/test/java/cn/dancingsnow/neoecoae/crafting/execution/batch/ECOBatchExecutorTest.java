package cn.dancingsnow.neoecoae.crafting.execution.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOIndeterminateBatchException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOBatchExecutorTest {
    @Test
    void acceptedLinearPrefixRefundsOnlyItsRejectedSuffix() {
        AEKey key = new TestKey();
        var inventory = inventory(key, 10);
        var service = energy();
        var ledger = new ECOBatchEnergyLedger();
        var reservation = ledger.reserve(service, 2.0D, BigInteger.TEN);

        var result = ECOBatchExecutor.execute(
                inventory,
                List.of(new GenericStack(key, 10)),
                Map.of(),
                10,
                true,
                reservation,
                () -> ECOBatchAdmission.accepted(3, true));

        assertEquals(3, result.acceptedCrafts());
        assertEquals(7, inventory.list.get(key));
        assertEquals(0, ledger.pendingRefund().signum());
        verify(service).injectPower(14.0D, Actionable.MODULATE);
    }

    @Test
    void rejectionRestoresInputsAndEnergy() {
        AEKey key = new TestKey();
        var inventory = inventory(key, 4);
        var service = energy();
        var ledger = new ECOBatchEnergyLedger();
        var reservation = ledger.reserve(service, 2.0D, BigInteger.valueOf(4));

        var result = ECOBatchExecutor.execute(
                inventory,
                List.of(new GenericStack(key, 4)),
                Map.of(),
                4,
                true,
                reservation,
                ECOBatchAdmission::rejected);

        assertEquals(ECOBatchAdmission.Status.REJECTED, result.status());
        assertEquals(4, inventory.list.get(key));
        assertEquals(0, ledger.pendingRefund().signum());
    }

    @Test
    void uncertainProviderRetainsInputsAndPrepaidEnergy() {
        AEKey key = new TestKey();
        var inventory = inventory(key, 4);
        var service = energy();
        var ledger = new ECOBatchEnergyLedger();
        var reservation = ledger.reserve(service, 2.0D, BigInteger.valueOf(4));

        assertThrows(
                ECOIndeterminateBatchException.class,
                () -> ECOBatchExecutor.execute(
                        inventory, List.of(new GenericStack(key, 4)), Map.of(), 4, true, reservation, () -> {
                            throw new IllegalStateException("provider result lost");
                        }));
        assertEquals(0, inventory.list.get(key));
        assertEquals(0, ledger.pendingRefund().signum());
    }

    @Test
    void statefulBatchRejectsPartialAdmissionAndKeepsCustody() {
        AEKey tool = new TestKey();
        var inventory = inventory(tool, 1);
        var ledger = new ECOBatchEnergyLedger();
        var service = energy();
        var reservation = ledger.reserve(service, 2.0D, BigInteger.valueOf(5));

        assertThrows(
                ECOIndeterminateBatchException.class,
                () -> ECOBatchExecutor.execute(
                        inventory,
                        List.of(new GenericStack(tool, 1)),
                        Map.of(),
                        5,
                        false,
                        reservation,
                        () -> ECOBatchAdmission.accepted(2, true)));
        assertEquals(0, inventory.list.get(tool));
        verify(service, never()).injectPower(anyDouble(), eq(Actionable.MODULATE));
    }

    @Test
    void acceptedBatchSettlementFailureCannotBeRetriedAsARejection() {
        AEKey key = new TestKey();
        var inventory = inventory(key, 4);
        var service = energy();
        var ledger = new ECOBatchEnergyLedger();
        var reservation = ledger.reserve(service, 2.0D, BigInteger.valueOf(4));

        assertThrows(
                ECOIndeterminateBatchException.class,
                () -> ECOBatchExecutor.execute(
                        inventory,
                        List.of(new GenericStack(key, 3)),
                        Map.of(),
                        4,
                        true,
                        reservation,
                        () -> ECOBatchAdmission.accepted(2, true)));
        assertEquals(1, inventory.list.get(key));
    }

    @Test
    void exactLeaseHandlesMoreThanLongMaxAndRollsBack() {
        AEKey key = new TestKey();
        ECOExactInventory inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        inventory.insert(key, 3, Actionable.MODULATE);
        var original = inventory.amount(key);
        var ledger = new ECOBatchEnergyLedger();
        var reservation = ledger.reserve(energy(), 0.0D, BigInteger.ONE);

        var result = ECOBatchExecutor.execute(
                inventory, List.of(), Map.of(key, original), 1, false, reservation, ECOBatchAdmission::rejected);

        assertEquals(ECOBatchAdmission.Status.REJECTED, result.status());
        assertEquals(original, inventory.amount(key));
    }

    @Test
    void failedEnergyRefundSurvivesNbtAndCanBeRetried() {
        IEnergyService service = mock(IEnergyService.class);
        when(service.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(call -> call.getArgument(0));
        when(service.injectPower(anyDouble(), eq(Actionable.MODULATE))).thenAnswer(call -> call.getArgument(0));
        var ledger = new ECOBatchEnergyLedger();
        var reservation = ledger.reserve(service, 3.0D, BigInteger.valueOf(4));
        reservation.rollback();
        assertEquals(0, ledger.pendingRefund().compareTo(new java.math.BigDecimal("12")));

        CompoundTag tag = new CompoundTag();
        ledger.writeToNBT(tag);
        var restored = new ECOBatchEnergyLedger();
        restored.readFromNBT(tag);
        when(service.injectPower(anyDouble(), eq(Actionable.MODULATE))).thenReturn(0.0D);
        restored.refundPending(service);
        assertEquals(0, restored.pendingRefund().signum());
    }

    private static ListCraftingInventory inventory(AEKey key, long amount) {
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, amount, Actionable.MODULATE);
        return inventory;
    }

    private static IEnergyService energy() {
        IEnergyService service = mock(IEnergyService.class);
        when(service.extractAEPower(anyDouble(), eq(Actionable.MODULATE), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(call -> call.getArgument(0));
        when(service.injectPower(anyDouble(), eq(Actionable.MODULATE))).thenReturn(0.0D);
        return service;
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
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return null;
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {}

        @Override
        protected Component computeDisplayName() {
            return Component.literal("test");
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }
}
