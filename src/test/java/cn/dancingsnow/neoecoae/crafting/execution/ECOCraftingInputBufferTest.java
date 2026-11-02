package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ECOCraftingInputBufferTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static IPatternDetails.IInput ingredient(AEKey key, long amount) {
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(key, amount)});
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(any(), any())).thenReturn(true);
        return input;
    }

    private static IPatternDetails pattern(IPatternDetails.IInput... inputs) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
        return pattern;
    }

    @ParameterizedTest @ValueSource(longs = {5, 6, 11})
    void repeatedIngredientsMatchAe2WithoutDebitingTheSource(long available) {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var pattern = pattern(ingredient(key, 3), ingredient(key, 3));
        var source = new ListCraftingInventory(ignored -> {});
        source.insert(key, available, Actionable.MODULATE);
        var copied = new ListCraftingInventory(ignored -> {});
        copied.list.addAll(source.list);
        var expected = CraftingCpuHelper.extractPatternInputs(pattern, copied, null,
                new KeyCounter(), new KeyCounter());
        var buffer = new ECOCraftingInputBuffer();
        var actual = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(pattern,
                new ECOCraftingInputPreview(source), null, buffer, new ECOCraftingRemainderCache());
        if (expected == null) {
            assertNull(actual);
        } else {
            assertNotNull(actual);
            assertEquals(expected.length, actual.length);
            for (int slot = 0; slot < expected.length; slot++) {
                assertEquals(expected[slot].get(key), actual[slot].get(key));
            }
        }
        assertEquals(available, source.list.get(key));
    }

    @Test void fuzzyValidationAndContainerRemaindersSurviveBufferReuse() {
        var primary = new Object();
        var encoded = mock(AEKey.class);
        var accepted = mock(AEKey.class);
        when(encoded.getPrimaryKey()).thenReturn(primary);
        when(accepted.getPrimaryKey()).thenReturn(primary);
        var bucket = AEItemKey.of(Items.BUCKET);
        var input = ingredient(encoded, 2);
        when(input.isValid(encoded, null)).thenReturn(false);
        when(input.getRemainingKey(accepted)).thenReturn(bucket);
        var pattern = pattern(input);
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(encoded, 4, Actionable.MODULATE);
        inventory.insert(accepted, 4, Actionable.MODULATE);
        var buffer = new ECOCraftingInputBuffer();
        var remainders = new ECOCraftingRemainderCache();
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(pattern,
                    new ECOCraftingInputPreview(inventory), null, buffer, remainders);
            assertNotNull(result);
            assertEquals(0, result[0].get(encoded));
            assertEquals(2, result[0].get(accepted));
            assertEquals(1, buffer.remainders().get(bucket));
        }
        verify(input, times(3)).isValid(accepted, null);
        verify(input, times(1)).getRemainingKey(accepted);
        assertEquals(4, inventory.list.get(accepted));
    }

    @Test void failedAttemptAndChangingSlotCountsDoNotLeakInputsOrRemainders() {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var missing = AEItemKey.of(Items.DIAMOND);
        var bucket = AEItemKey.of(Items.BUCKET);
        var first = ingredient(key, 1);
        when(first.getRemainingKey(key)).thenReturn(bucket);
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 8, Actionable.MODULATE);
        var buffer = new ECOCraftingInputBuffer();
        var cache = new ECOCraftingRemainderCache();
        var preview = new ECOCraftingInputPreview(inventory);
        assertNull(ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(
                pattern(first, ingredient(missing, 1)), preview, null, buffer, cache));
        preview.reset();
        var result = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(
                pattern(ingredient(key, 2)), preview, null, buffer, cache);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(2, result[0].get(key));
        assertTrue(buffer.remainders().isEmpty());
        preview.reset();
        result = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(
                pattern(ingredient(key, 2), ingredient(key, 3), ingredient(key, 1)),
                preview, null, buffer, cache);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(3, result[1].get(key));
        assertEquals(1, result[2].get(key));
        assertEquals(8, inventory.list.get(key));
    }

    @Test void dispatchedContextOwnsSnapshotsAfterTheBufferIsReused() {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var other = AEItemKey.of(Items.DIAMOND);
        var buffer = new ECOCraftingInputBuffer();
        var inputs = buffer.prepare(1);
        inputs[0].add(key, 3);
        buffer.outputs().add(other, 2);
        buffer.remainders().add(key, 1);
        var context = ECOBatchDispatchContext.create(pattern(ingredient(key, 3)), inputs,
                buffer.outputs(), buffer.remainders(), null, null);
        assertSame(inputs, buffer.prepare(1));
        inputs[0].add(other, 9);
        assertEquals(3, context.inputCounters()[0].get(key));
        assertEquals(0, context.inputCounters()[0].get(other));
        assertEquals(List.of(new GenericStack(other, 2)), context.outputs());
        assertEquals(List.of(new GenericStack(key, 1)), context.containerItems());
    }

    @Test void inputResolutionNeverHashes4096UnrelatedInventoryKeys() {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 8, Actionable.MODULATE);
        var unrelated = new TrackedKey[4096];
        for (int index = 0; index < unrelated.length; index++) {
            unrelated[index] = new TrackedKey(index);
            inventory.insert(unrelated[index], 100, Actionable.MODULATE);
            unrelated[index].forbidHash = true;
        }
        var buffer = new ECOCraftingInputBuffer();
        var preview = new ECOCraftingInputPreview(inventory);
        var pattern = pattern(ingredient(key, 3));
        var remainders = new ECOCraftingRemainderCache();
        for (int attempt = 0; attempt < 128; attempt++) {
            preview.reset();
            var result = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(
                    pattern, preview, null, buffer, remainders);
            assertNotNull(result);
            assertEquals(3, result[0].get(key));
        }
        for (var untouched : unrelated) {
            untouched.forbidHash = false;
            assertEquals(100, inventory.list.get(untouched));
        }
    }

    private static final class TrackedKey extends AEKey {
        private final int identifier;
        private final Object primary = new Object();
        private boolean forbidHash;

        private TrackedKey(int identifier) { this.identifier = identifier; }
        @Override public int hashCode() {
            if (forbidHash) throw new AssertionError("Unrelated inventory key was visited");
            return identifier;
        }
        @Override public boolean equals(Object other) { return this == other; }
        @Override public AEKeyType getType() { return AEKeyType.items(); }
        @Override public AEKey dropSecondary() { return this; }
        @Override public Object getPrimaryKey() { return primary; }
        @Override public ResourceLocation getId() { return ResourceLocation.fromNamespaceAndPath("test", "key_" + identifier); }
        @Override protected Component computeDisplayName() { return Component.literal("unrelated"); }
        @Override public boolean hasComponents() { return false; }
        @Override public CompoundTag toTag(HolderLookup.Provider registries) { throw new UnsupportedOperationException(); }
        @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) { throw new UnsupportedOperationException(); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos position) {
            throw new UnsupportedOperationException();
        }
    }
}
