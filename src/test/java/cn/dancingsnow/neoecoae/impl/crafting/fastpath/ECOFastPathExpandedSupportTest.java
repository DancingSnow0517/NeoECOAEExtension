package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOFastPathExpandedSupportTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(LoadingModList.class)) {
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loading.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    @Test
    void fixedDamagedOutputIsCacheable() {
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        tool.setDamageValue(17);
        assertTrue(ECOFastPathStacks.areValidItemStacks(List.of(GenericStack.fromItemStack(tool)),
            Integer.MAX_VALUE, true, ECOFastPathStacks.ItemStackValidation.FAST_PATH));
    }

    @Test
    void mixedToolAndCatalystAreConsumedAndReturnedOnce() {
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        tool.setDamageValue(tool.getMaxDamage() - 2);
        ItemStack worn = tool.copy();
        worn.setDamageValue(tool.getDamageValue() + 1);
        ItemStack catalyst = new ItemStack(Items.DIAMOND);
        ItemStack ingredient = new ItemStack(Items.COBBLESTONE);
        var analysis = ECOReusableStateAnalyzer.analyze(
            List.of(tool, catalyst, ingredient), List.of(worn, catalyst.copy(), ItemStack.EMPTY));
        assertFalse(analysis.rejected());
        var model = analysis.model();
        assertNotNull(model);
        assertEquals(2, model.maxBatchSize());
        var inputs = model.batchInputs(List.of(GenericStack.fromItemStack(tool),
            GenericStack.fromItemStack(catalyst), GenericStack.fromItemStack(ingredient)), 2);
        assertEquals(1, amount(inputs, tool));
        assertEquals(1, amount(inputs, catalyst));
        assertEquals(2, amount(inputs, ingredient));
        var remaining = model.batchRemainders(List.of(GenericStack.fromItemStack(worn),
            GenericStack.fromItemStack(catalyst)), 2);
        assertEquals(List.of(GenericStack.fromItemStack(catalyst)), remaining);
    }

    @Test
    void numericChangesPreserveUnchangedNestedMetadata() {
        CompoundTag before = new CompoundTag();
        before.putString("owner", "player");
        CompoundTag energy = new CompoundTag();
        energy.putInt("stored", 100);
        energy.putString("type", "mana");
        before.put("energy", energy);
        CompoundTag after = before.copy();
        after.getCompound("energy").putInt("stored", 90);
        CompoundTag result = ECOStateTransitionBatchModel.applyCustomDataTransition(before, after, 4).orElseThrow();
        assertEquals(60, result.getCompound("energy").getInt("stored"));
        assertEquals("mana", result.getCompound("energy").getString("type"));
        assertEquals("player", result.getString("owner"));
        after.putString("owner", "another");
        assertTrue(ECOStateTransitionBatchModel.applyCustomDataTransition(before, after, 4).isEmpty());
    }

    private static long amount(List<GenericStack> stacks, ItemStack item) {
        var key = GenericStack.fromItemStack(item).what();
        return stacks.stream().filter(stack -> stack.what().equals(key)).mapToLong(GenericStack::amount).sum();
    }
}
