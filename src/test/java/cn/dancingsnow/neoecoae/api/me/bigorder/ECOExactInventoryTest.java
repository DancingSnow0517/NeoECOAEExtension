package cn.dancingsnow.neoecoae.api.me.bigorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
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
    void debitAndRestorePreserveAmountsBeyondLongMax() {
        AEKey key = new TestKey();
        ECOExactInventory inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        inventory.insert(key, 17, Actionable.MODULATE);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(17)), inventory.amount(key));
        assertEquals(Long.MAX_VALUE, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        assertFalse(inventory.debit(Map.of(key, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(18)))));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(17)), inventory.amount(key));
        assertTrue(inventory.debit(Map.of(key, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))));
        assertEquals(BigInteger.valueOf(16), inventory.amount(key));
        inventory.restore(Map.of(key, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(17)), inventory.snapshot().get(key));
        assertEquals(Long.MAX_VALUE, inventory.extract(key, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(BigInteger.valueOf(17), inventory.amount(key));
    }

    private static final class TestKey extends AEKey {
        @Override public AEKeyType getType() { return null; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return this; }
        @Override public ResourceLocation getId() { return null; }
        @Override public void writeToPacket(FriendlyByteBuf buffer) {}
        @Override protected Component computeDisplayName() { return Component.literal("test"); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }
}
