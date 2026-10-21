package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.AutoCraftingMenu;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ForgeFastPathStateBoundaryTest {
    @BeforeAll static void bootstrap() throws Exception {
        PlatformTestBootstrap.initializeKeyRegistry();
    }

    @Test void nestedNumericStatePreservesNameAndMetadataWithoutAliasingInputs() {
        var before = state(100);
        before.setHoverName(net.minecraft.network.chat.Component.literal("Reusable catalyst"));
        before.getOrCreateTag().putString("owner", "player");
        var after = before.copy();
        after.getTag().getCompound("energy").putInt("stored", 90);
        var model = ECOStateTransitionBatchModel.analyze(List.of(before), List.of(after)).orElseThrow();
        var remainder = model.applyTransition(0, 4);
        assertEquals(60, remainder.getTag().getCompound("energy").getInt("stored"));
        assertEquals("player", remainder.getTag().getString("owner"));
        assertEquals(before.getHoverName(), remainder.getHoverName());
        assertEquals(100, before.getTag().getCompound("energy").getInt("stored"));
        assertEquals(90, after.getTag().getCompound("energy").getInt("stored"));
        assertTrue(model.requiresSecondStepProof());
    }

    @Test void changingVanillaItemDataDoesNotBecomeALinearCounter() {
        for (String field : List.of("CustomModelData", "RepairCost", "HideFlags", "map", "Damage")) {
            var before = state(100);
            before.getTag().putInt(field, 1);
            var after = before.copy();
            after.getTag().putInt(field, 2);
            assertTrue(ECOStateTransitionBatchModel.analyze(List.of(before), List.of(after)).isEmpty(), field);
        }
        var before = state(100);
        before.getTag().put("display", new CompoundTag());
        before.getTag().getCompound("display").putInt("color", 1);
        var after = before.copy();
        after.getTag().getCompound("display").putInt("color", 2);
        assertTrue(ECOStateTransitionBatchModel.analyze(List.of(before), List.of(after)).isEmpty());
    }

    @Test void missingChangingShapeOrNonNumericMetadataRejectsFastPath() {
        var before = state(100);
        var after = before.copy();
        after.getTag().putString("new", "field");
        assertTrue(ECOStateTransitionBatchModel.analyze(List.of(before), List.of(after)).isEmpty());
        after = before.copy();
        after.getTag().getCompound("energy").putLong("stored", 90);
        assertTrue(ECOStateTransitionBatchModel.analyze(List.of(before), List.of(after)).isEmpty());
        after = before.copy();
        after.getTag().getCompound("energy").putString("type", "changed");
        assertTrue(ECOStateTransitionBatchModel.analyze(List.of(before), List.of(after)).isEmpty());
    }

    @Test void unchangedCatalystIsReservedAndReturnedOnceAcrossBatch() {
        var catalyst = new ItemStack(Items.DIAMOND);
        var ingredient = new ItemStack(Items.COBBLESTONE);
        var model = ECOStateTransitionBatchModel.analyze(List.of(catalyst, ingredient),
            List.of(catalyst.copy(), ItemStack.EMPTY)).orElseThrow();
        var inputs = model.batchInputs(List.of(GenericStack.fromItemStack(catalyst),
            GenericStack.fromItemStack(ingredient)), 1_000);
        assertEquals(1, amount(inputs, catalyst));
        assertEquals(1_000, amount(inputs, ingredient));
        assertEquals(List.of(GenericStack.fromItemStack(catalyst)),
            model.batchRemainders(List.of(GenericStack.fromItemStack(catalyst)), 1_000));
        assertFalse(model.requiresSecondStepProof());
    }

    @Test void observedCountConsumptionLimitsBatchAndReturnsExactRemainder() {
        var before = new ItemStack(Items.PAPER, 8);
        var after = before.copyWithCount(6);
        var model = ECOStateTransitionBatchModel.analyze(List.of(before), List.of(after)).orElseThrow();
        assertEquals(4, model.maxBatchSize());
        assertEquals(2, amount(model.batchRemainders(List.of(GenericStack.fromItemStack(after)), 3), before));
        assertTrue(model.batchRemainders(List.of(GenericStack.fromItemStack(after)), 4).isEmpty());
        assertEquals(8, amount(model.batchInputs(List.of(GenericStack.fromItemStack(before)), 3), before));
    }

    @Test void stateOverflowIsRejectedWithoutChangingOriginalStack() {
        var before = state(Integer.MAX_VALUE - 1);
        var after = state(Integer.MAX_VALUE);
        var model = ECOStateTransitionBatchModel.analyze(List.of(before), List.of(after)).orElseThrow();
        assertThrows(ArithmeticException.class, () -> model.applyTransition(0, 2));
        assertEquals(Integer.MAX_VALUE - 1, before.getTag().getCompound("energy").getInt("stored"));
    }

    @Test void secondStateStepMustMatchTheObservedDelta() {
        var first = ECOStateTransitionBatchModel.analyze(List.of(state(100)), List.of(state(90))).orElseThrow();
        var same = ECOStateTransitionBatchModel.analyze(List.of(state(90)), List.of(state(80))).orElseThrow();
        var changed = ECOStateTransitionBatchModel.analyze(List.of(state(90)), List.of(state(79))).orElseThrow();
        assertTrue(first.sameTransition(same));
        assertFalse(first.sameTransition(changed));
    }

    @Test void mixedToolAndCatalystKeepDurabilityAndQuantityRules() {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        tool.setDamageValue(tool.getMaxDamage() - 2);
        var worn = tool.copy();
        worn.setDamageValue(tool.getDamageValue() + 1);
        var catalyst = new ItemStack(Items.DIAMOND);
        var ingredient = new ItemStack(Items.COBBLESTONE);
        var analysis = ECOReusableStateAnalyzer.analyze(List.of(tool, catalyst, ingredient),
            List.of(worn, catalyst.copy(), ItemStack.EMPTY));
        assertFalse(analysis.rejected());
        assertNotNull(analysis.model());
        assertEquals(2, analysis.model().maxBatchSize());
        var inputs = analysis.model().batchInputs(List.of(GenericStack.fromItemStack(tool),
            GenericStack.fromItemStack(catalyst), GenericStack.fromItemStack(ingredient)), 2);
        assertEquals(1, amount(inputs, tool));
        assertEquals(1, amount(inputs, catalyst));
        assertEquals(2, amount(inputs, ingredient));
        assertEquals(List.of(GenericStack.fromItemStack(catalyst)),
            analysis.model().batchRemainders(List.of(GenericStack.fromItemStack(worn),
                GenericStack.fromItemStack(catalyst)), 2));
    }

    @Test void metadataOnDamagedToolCannotBeIgnoredByDurabilityModel() {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        tool.setDamageValue(3);
        tool.getOrCreateTag().putInt("other", 10);
        var after = tool.copy();
        after.setDamageValue(4);
        after.getTag().putInt("other", 9);
        assertTrue(ECODurabilityBatchModel.analyze(List.of(tool), List.of(after)).isEmpty());
    }

    @Test void fullGridRemaindersKeepOffsetAndCopyEachStack() {
        var grid = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        grid.setItem(8, new ItemStack(Items.WATER_BUCKET));
        var original = new ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        original.set(8, new ItemStack(Items.BUCKET));
        var copied = ECOCraftingStateSlots.expandRemainingItems(grid, original, 3, 3);
        assertEquals(9, copied.size());
        assertTrue(copied.subList(0, 8).stream().allMatch(ItemStack::isEmpty));
        assertTrue(copied.get(8).is(Items.BUCKET));
        copied.get(8).setCount(2);
        assertEquals(1, original.get(8).getCount());
        assertThrows(IllegalArgumentException.class,
            () -> ECOCraftingStateSlots.expandRemainingItems(grid, List.of(new ItemStack(Items.BUCKET)), 3, 3));
    }

    @Test void persistedBatchUsesNativeNbtAndRejectsUnknownKeysAndNegativeAmounts() {
        var input = new GenericStack(AEItemKey.of(state(100)), Long.MAX_VALUE);
        ListTag saved = ECOFastPathStacks.writeGenericStacks(List.of(input));
        assertEquals(List.of(input), ECOFastPathStacks.readValidatedBatchInputStacks(saved, true).orElseThrow());
        var invalid = saved.copy();
        invalid.getCompound(0).putLong("#", -1);
        assertTrue(ECOFastPathStacks.readValidatedBatchInputStacks(invalid, true).isEmpty());
        invalid = saved.copy();
        invalid.getCompound(0).putString("#c", "missing_mod:resource");
        assertTrue(ECOFastPathStacks.readValidatedBatchInputStacks(invalid, true).isEmpty());
    }

    @Test void deterministicOutputsAndPersistedInputsRetainExactMetadata() {
        var key = new GenericStack(AEItemKey.of(state(100)), 1);
        assertTrue(ECOFastPathStacks.areValidItemStacks(List.of(key), Long.MAX_VALUE, true,
            ECOFastPathStacks.ItemStackValidation.FAST_PATH));
        var saved = ECOFastPathStacks.writeGenericStacks(List.of(key));
        assertEquals(List.of(key), ECOFastPathStacks.readValidatedBatchItemStacks(saved, true).orElseThrow());
    }

    private static ItemStack state(int energy) {
        var stack = new ItemStack(Items.PAPER);
        var data = stack.getOrCreateTag();
        var nested = new CompoundTag();
        nested.putInt("stored", energy);
        nested.putString("type", "mana");
        data.put("energy", nested);
        return stack;
    }

    private static long amount(List<GenericStack> stacks, ItemStack item) {
        var key = AEItemKey.of(item);
        return stacks.stream().filter(stack -> stack.what().equals(key)).mapToLong(GenericStack::amount).sum();
    }
}
