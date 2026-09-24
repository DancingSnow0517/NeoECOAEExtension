package cn.dancingsnow.neoecoae.api.me.bigorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
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

class ECOExactInventoryTest {
    @Test
    void unrepresentableCycleCannotAcquireCpuOwnership() {
        AEKey key = new TestKey();
        var plan = new CraftingPlan(
                new GenericStack(key, 1L),
                0L,
                true,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
        var cycle = new ComponentPlanningResult(
                0,
                ComponentPlanningResult.Type.CYCLIC,
                ComponentPlanningResult.Status.UNREPRESENTABLE,
                Map.of(),
                null,
                "wide cycle");
        var result = new ECOPlanningResult(
                PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
                plan,
                new ECOPlanTrace(),
                List.of(),
                List.of(cycle),
                List.of(),
                0L);

        assertFalse(ECOBigOrderAdmission.allows(result, false));
        assertFalse(ECOBigOrderAdmission.allows(result, true));
    }

    @Test
    void debitAndRestorePreserveAmountsBeyondLongMax() {
        AEKey key = new TestKey();
        ECOExactInventory inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        inventory.insert(key, 17, Actionable.MODULATE);
        assertTrue(inventory.hasContents());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(17)), inventory.amount(key));
        assertEquals(Long.MAX_VALUE, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        assertFalse(
                inventory.debit(Map.of(key, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(18)))));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(17)), inventory.amount(key));
        assertTrue(
                inventory.debit(Map.of(key, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))));
        assertEquals(BigInteger.valueOf(16), inventory.amount(key));
        inventory.restore(Map.of(key, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(17)),
                inventory.snapshot().get(key));
        assertEquals(Long.MAX_VALUE, inventory.extract(key, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(17), inventory.amount(key));
        assertTrue(inventory.hasContents());
        assertEquals(17, inventory.extract(key, 17, Actionable.MODULATE));
        assertFalse(inventory.hasContents());
    }

    private static final class TestKey extends AEKey {
        @Override
        public AEKeyType getType() {
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
