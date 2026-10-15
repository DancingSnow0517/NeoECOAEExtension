package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ECOExecutionContractGenerationTest {
    @Test void preparedExecutionLosesEligibilityAfterContractChange() {
        ECOFastPathKey key = ECOFastPathKey.of(new Object(), new KeyCounter[0], null,
            AE2PatternIntrospection.reloadGeneration()).orElseThrow();
        ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.ofNormalizedComponents(
            key, List.of(), List.of(), List.of());
        assertTrue(execution.fastPathEligible());
        AE2PatternIntrospection.onRecipeReloadOrServerReload();
        assertFalse(execution.fastPathEligible());
    }

    @Test void oldGenerationCredentialCannotBeReissuedAfterCacheWasCleared() {
        long generation = AE2PatternIntrospection.reloadGeneration();
        ECOFastPathKey key = ECOFastPathKey.of(new Object(), new KeyCounter[0], null, generation).orElseThrow();
        ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.ofNormalizedComponents(
            key, List.of(), List.of(), List.of());
        ECOCraftingFastPathCache cache = new ECOCraftingFastPathCache(16);
        ECOFastPathResult result = ECOFastPathResult.positive(List.of(), List.of(), List.of(), 0L);
        ECOVerifiedFastPathRecipe before = ECOVerifiedFastPathRecipe.trusted(cache, execution, key, result, generation);
        assertTrue(before.isIssuedBy(cache));
        AE2PatternIntrospection.onRecipeReloadOrServerReload();
        assertFalse(before.isIssuedBy(cache));
        // Simulate a lookup that finished late, after clear, using an obsolete caller-supplied generation.
        ECOVerifiedFastPathRecipe late = ECOVerifiedFastPathRecipe.trusted(cache, execution, key, result, generation);
        assertFalse(late.isIssuedBy(cache));
    }

    @Test void cacheWritesAndRegistryInvalidationMayOverlap() throws Exception {
        ECOCraftingFastPathCache cache = new ECOCraftingFastPathCache(16);
        ECOFastPathKey key = ECOFastPathKey.of(new Object(), new KeyCounter[0], null,
            AE2PatternIntrospection.reloadGeneration()).orElseThrow();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writes = executor.submit(() -> {
                for (int i = 0; i < 500; i++) {
                    cache.putNegative(key, i);
                    cache.get(key, i);
                }
            });
            var invalidations = executor.submit(() -> {
                for (int i = 0; i < 500; i++) AE2PatternIntrospection.onRecipeReloadOrServerReload();
            });
            writes.get(10, TimeUnit.SECONDS);
            invalidations.get(10, TimeUnit.SECONDS);
        }
        cache.putNegative(key, 1000L, "STILL_USABLE");
        assertEquals("STILL_USABLE", cache.get(key, 1000L).rejectReason());
    }
}
