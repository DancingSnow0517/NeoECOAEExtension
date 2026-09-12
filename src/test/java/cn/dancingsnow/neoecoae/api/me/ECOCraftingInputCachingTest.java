package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOCraftingInputCachingTest {
    @Test
    void virtualRemovalAndRollbackDoNotQueryFuzzyMetadata() {
        var key = key();
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 12L, Actionable.MODULATE);
        clearInvocations(key);
        var preview = new ECOCraftingInputPreview(inventory);
        assertEquals(2L, preview.extractTemplates(new InputTemplate(key, 3L), 2L));
        preview.insert(key, 6L, Actionable.MODULATE);
        assertEquals(12L, preview.extract(key, 20L, Actionable.SIMULATE));
        assertEquals(12L, inventory.list.get(key));
        verify(key, never()).getFuzzySearchMaxValue();
    }

    private static AEKey key() {
        AEKey key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
        return key;
    }

    private static IPatternDetails.IInput input(AEKey key, long amount, long multiplier) {
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(key, amount)});
        when(input.getMultiplier()).thenReturn(multiplier);
        when(input.isValid(any(), any())).thenReturn(true);
        return input;
    }

    @Test
    void candidatesAreReusedButValidityIsCheckedAgain() {
        var key = key();
        var source = mock(ICraftingInventory.class);
        when(source.findFuzzyTemplates(key)).thenReturn(List.of(key));
        when(source.extract(eq(key), anyLong(), eq(Actionable.SIMULATE))).thenReturn(8L);
        var input = input(key, 2, 2);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
        when(pattern.getOutputs()).thenReturn(List.of());
        var cache = new ECOCraftingInputTemplateCache();
        var remainderCache = new ECOCraftingRemainderCache();

        var first = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(pattern,
            new ECOCraftingInputPreview(source, cache), null, new KeyCounter(), new KeyCounter(), remainderCache);
        assertNotNull(first);
        assertEquals(4L, first[0].get(key));
        when(input.isValid(any(), any())).thenReturn(false);
        assertNull(ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(pattern,
            new ECOCraftingInputPreview(source, cache), null, new KeyCounter(), new KeyCounter(), remainderCache));
        verify(source, times(1)).findFuzzyTemplates(key);
        verify(source, never()).extract(any(), anyLong(), eq(Actionable.MODULATE));
    }

    @Test
    void inventoryChangesInvalidateEmptyAndPopulatedSnapshots() {
        var key = key();
        var cache = new ECOCraftingInputTemplateCache();
        var inventory = new ListCraftingInventory(cache::inventoryChanged);
        var input = input(key, 1, 1);
        assertTrue(cache.get(inventory, input).isEmpty());
        inventory.insert(key, 3L, Actionable.MODULATE);
        assertEquals(1, cache.get(inventory, input).size());
        inventory.extract(key, 3L, Actionable.MODULATE);
        assertTrue(cache.get(inventory, input).isEmpty());
        inventory.insert(key, 2L, Actionable.MODULATE);
        assertEquals(1, cache.get(inventory, input).size());
    }

    @Test
    void quantityChangesReuseCandidatesButExtractionUsesLiveStock() {
        var key = key();
        var cache = new ECOCraftingInputTemplateCache();
        var inventory = new ListCraftingInventory(cache::inventoryChanged);
        var input = input(key, 2, 10);
        inventory.insert(key, 10L, Actionable.MODULATE);
        var candidates = cache.get(inventory, input);
        inventory.extract(key, 7L, Actionable.MODULATE);
        assertSame(candidates, cache.get(inventory, input));
        var preview = new ECOCraftingInputPreview(inventory, cache);
        assertEquals(1L, preview.extractTemplates(candidates.getFirst(), 10L));
        inventory.insert(key, 4L, Actionable.MODULATE);
        assertSame(candidates, cache.get(inventory, input));
    }

    @Test
    void newVariantInvalidatesCandidatesEvenWhenPrimaryItemIsUnchanged() {
        var key = key();
        var variant = key();
        Object primary = key.getPrimaryKey();
        when(variant.getPrimaryKey()).thenReturn(primary);
        var cache = new ECOCraftingInputTemplateCache();
        var inventory = new ListCraftingInventory(cache::inventoryChanged);
        var input = input(key, 1, 1);
        inventory.insert(key, 10L, Actionable.MODULATE);
        assertEquals(1, cache.get(inventory, input).size());
        inventory.insert(variant, 1L, Actionable.MODULATE);
        assertEquals(2, cache.get(inventory, input).size());
    }

    @Test
    void repeatedSuccessfulConsumptionEnumeratesCandidatesOnlyOnce() {
        var key = key();
        var cache = new ECOCraftingInputTemplateCache();
        var inventory = spy(new ListCraftingInventory(cache::inventoryChanged));
        var input = input(key, 1, 1);
        inventory.insert(key, 1001L, Actionable.MODULATE);
        for (int i = 0; i < 1000; i++) {
            var candidate = cache.get(inventory, input).getFirst();
            assertEquals(1L, new ECOCraftingInputPreview(inventory, cache).extractTemplates(candidate, 1L));
            inventory.extract(key, 1L, Actionable.MODULATE);
        }
        verify(inventory, times(1)).findFuzzyTemplates(key);
        assertEquals(1L, inventory.list.get(key));
    }

    @Test
    void previewExtractionPreservesProtectedStartupStock() {
        var key = key();
        var input = input(key, 3, 10);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 11L, Actionable.MODULATE);
        var preview = new ECOCraftingInputPreview(inventory, pattern, Map.of(key, 5L),
            new ECOCraftingRemainderCache());
        assertEquals(2L, preview.extractTemplates(new InputTemplate(key, 3L), 10L));
        assertEquals(0L, preview.extractTemplates(new InputTemplate(key, 1L), 1L));
        assertEquals(11L, inventory.list.get(key));
    }

    @Test
    void sourceReplacementAndReloadInvalidateSnapshots() throws Exception {
        var key = key();
        var input = input(key, 1, 1);
        var first = mock(ICraftingInventory.class);
        var second = mock(ICraftingInventory.class);
        when(first.findFuzzyTemplates(key)).thenReturn(List.of(key));
        when(second.findFuzzyTemplates(key)).thenReturn(List.of());
        var cache = new ECOCraftingInputTemplateCache();
        assertEquals(1, cache.get(first, input).size());
        assertTrue(cache.get(second, input).isEmpty());
        when(second.findFuzzyTemplates(key)).thenReturn(List.of(key));
        var generation = AE2PatternIntrospection.class.getDeclaredField("reloadGeneration");
        generation.setAccessible(true);
        long before = generation.getLong(null);
        try {
            generation.setLong(null, before + 1);
            assertEquals(1, cache.get(second, input).size());
        } finally {
            generation.setLong(null, before);
        }
    }

    @Test
    void previewExtractionMatchesAe2RoundingAndRepeatedSlots() {
        var key = key();
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 11L, Actionable.MODULATE);
        var expected = new ECOCraftingInputPreview(inventory);
        var actual = new ECOCraftingInputPreview(inventory);
        var template = new InputTemplate(key, 3L);
        for (long requested : new long[] {2L, 2L, 1L}) {
            assertEquals(CraftingCpuHelper.extractTemplates(expected, template, requested),
                actual.extractTemplates(template, requested));
        }
        assertEquals(2L, actual.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(11L, inventory.list.get(key));
    }

    @Test
    void candidatesKeepSubstitutionOrderAndAmounts() {
        var first = key();
        var second = key();
        var variant = key();
        var input = input(first, 2, 1);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {
            new GenericStack(first, 2), new GenericStack(second, 5)});
        var inventory = mock(ICraftingInventory.class);
        when(inventory.findFuzzyTemplates(first)).thenReturn(List.of(first, variant));
        when(inventory.findFuzzyTemplates(second)).thenReturn(List.of(second, variant));
        var templates = new ECOCraftingInputTemplateCache().get(inventory, input);
        assertEquals(List.of(new InputTemplate(first, 2), new InputTemplate(variant, 2),
            new InputTemplate(second, 5), new InputTemplate(variant, 5)), templates);
    }

    @Test
    void zeroExtractionDoesNotComputeRemainders() {
        var key = key();
        var input = input(key, 3, 1);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 2L, Actionable.MODULATE);
        assertNull(ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(pattern,
            new ECOCraftingInputPreview(inventory, new ECOCraftingInputTemplateCache()), null,
            new KeyCounter(), new KeyCounter(), new ECOCraftingRemainderCache()));
        verify(input, never()).getRemainingKey(any());
        assertEquals(2L, inventory.list.get(key));
    }

    @Test
    void remainderHotCacheIncludesNullAndInvalidatesOnReload() throws Exception {
        var key = key();
        var returned = key();
        var input = input(key, 1, 1);
        var cache = new ECOCraftingRemainderCache();
        assertNull(cache.get(input, key));
        assertNull(cache.get(input, key));
        verify(input, times(1)).getRemainingKey(key);
        when(input.getRemainingKey(key)).thenReturn(returned);
        var generation = AE2PatternIntrospection.class.getDeclaredField("reloadGeneration");
        generation.setAccessible(true);
        long before = generation.getLong(null);
        try {
            generation.setLong(null, before + 1);
            assertSame(returned, cache.get(input, key));
        } finally {
            generation.setLong(null, before);
        }
    }

    @Test
    void candidateCacheEvictsAtCapacity() {
        var key = key();
        var inventory = mock(ICraftingInventory.class);
        when(inventory.findFuzzyTemplates(key)).thenReturn(List.of(key));
        var cache = new ECOCraftingInputTemplateCache();
        var first = input(key, 1, 1);
        cache.get(inventory, first);
        for (int i = 0; i < 256; i++) cache.get(inventory, input(key, 1, 1));
        cache.get(inventory, first);
        verify(inventory, times(258)).findFuzzyTemplates(key);
    }
}
