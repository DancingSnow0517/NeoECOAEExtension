package cn.dancingsnow.neoecoae.api.me.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapters;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOPatternSemanticsRegistryTest {
    private final List<ResourceLocation> registrations = new ArrayList<>();

    @AfterEach
    void cleanup() {
        registrations.forEach(ECOPatternSemanticsRegistry::unregister);
    }

    @Test
    void unregisteredPatternUsesOrdinaryAE2ContractAndRetainsIdentity() {
        IPatternDetails pattern = ordinaryPattern();
        var semantics = PatternSemanticAdapters.analyze(pattern);
        assertTrue(semantics.supported());
        assertSame(pattern, semantics.physicalPattern());
        assertEquals(List.of(pattern.getOutputs()), semantics.producedOutputs());
    }

    @Test
    void priorityThenIdChoosesExactlyOneAdapter() {
        register("z", 10, adapter((pattern, base) -> unsupported(pattern, "z")));
        register("low", 0, adapter((pattern, base) -> unsupported(pattern, "low")));
        register("a", 10, adapter((pattern, base) -> unsupported(pattern, "a")));
        assertEquals("a", PatternSemanticAdapters.analyze(ordinaryPattern()).unsupportedReason());
        var snapshot = ECOPatternSemanticsRegistry.snapshot();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void duplicateRegistrationDoesNotChangeRevision() {
        ResourceLocation id = register("duplicate", 0, adapter((pattern, base) -> base));
        long revision = ECOPatternSemanticsRegistry.revision();
        assertThrows(IllegalArgumentException.class,
            () -> ECOPatternSemanticsRegistry.register(id, 1, adapter((pattern, base) -> base)));
        assertEquals(revision, ECOPatternSemanticsRegistry.revision());
    }

    @Test
    void registrationAndRemovalInvalidatePlanningAndFastPathGenerations() {
        long revision = ECOPatternSemanticsRegistry.revision();
        long generation = AE2PatternIntrospection.reloadGeneration();
        ResourceLocation id = register("version", 0, adapter((pattern, base) -> base));
        assertEquals(revision + 1, ECOPatternSemanticsRegistry.revision());
        assertTrue(AE2PatternIntrospection.reloadGeneration() > generation);
        generation = AE2PatternIntrospection.reloadGeneration();
        assertTrue(ECOPatternSemanticsRegistry.unregister(id));
        assertEquals(revision + 2, ECOPatternSemanticsRegistry.revision());
        assertTrue(AE2PatternIntrospection.reloadGeneration() > generation);
        assertFalse(ECOPatternSemanticsRegistry.unregister(id));
        assertEquals(revision + 2, ECOPatternSemanticsRegistry.revision());
    }

    @Test
    void matchingAdapterFailureCannotFallBackToGenericSemantics() {
        register("failure", 0, adapter((pattern, base) -> {
            throw new IllegalStateException("rejected");
        }));
        var semantics = PatternSemanticAdapters.analyze(ordinaryPattern());
        assertFalse(semantics.supported());
        assertTrue(semantics.unsupportedReason().contains("pattern_api_test:failure:IllegalStateException"));
    }

    @Test
    void contractMutationDuringAnalysisRejectsTheOldResult() {
        register("mutation", 100, adapter((pattern, base) -> {
            register("new_contract", 0, adapter((ignored, ordinary) -> ordinary));
            return base;
        }));
        var semantics = PatternSemanticAdapters.analyze(ordinaryPattern());
        assertFalse(semantics.supported());
        assertEquals("PATTERN_CONTRACT_CHANGED_DURING_ANALYSIS", semantics.unsupportedReason());
    }

    @Test
    void supportsFailureIsAnExplicitUnsupportedResult() {
        AtomicInteger calls = new AtomicInteger();
        register("supports_failure", 0, new ECOPatternSemanticAdapter() {
            @Override
            public boolean supports(IPatternDetails pattern) {
                if (calls.incrementAndGet() == 1) throw new IllegalStateException("broken matcher");
                return true;
            }

            @Override
            public ECOPatternSemantics analyze(IPatternDetails pattern, ECOPatternSemantics base) {
                fail("A failed matcher must not analyze");
                return base;
            }
        });
        assertFalse(PatternSemanticAdapters.analyze(ordinaryPattern()).supported());
        assertEquals(1, calls.get(), "A failed matcher must never be retried as an ordinary or successful match");
    }

    @Test
    void changedOrFailingSupportsDuringAnalysisCannotFallBack() {
        ECOPatternSemanticAdapter adapter = mock(ECOPatternSemanticAdapter.class);
        when(adapter.supports(any())).thenReturn(true, false);
        ResourceLocation id = register("unstable", 0, adapter);
        assertTrue(PatternSemanticAdapters.analyze(ordinaryPattern()).unsupportedReason().endsWith("SUPPORTS_CHANGED"));
        verify(adapter, never()).analyze(any(), any());
        ECOPatternSemanticsRegistry.unregister(id);
        ECOPatternSemanticAdapter failing = mock(ECOPatternSemanticAdapter.class);
        when(failing.supports(any())).thenReturn(true).thenThrow(new IllegalStateException());
        register("late_failure", 0, failing);
        assertTrue(PatternSemanticAdapters.analyze(ordinaryPattern()).unsupportedReason().endsWith("IllegalStateException"));
        verify(failing, never()).analyze(any(), any());
    }

    @Test
    void adapterCannotReplacePhysicalPatternOrReturnNull() {
        ResourceLocation id = register("identity", 0,
            adapter((pattern, base) -> unsupported(ordinaryPattern(), "wrong pattern")));
        assertTrue(PatternSemanticAdapters.analyze(ordinaryPattern()).unsupportedReason()
            .endsWith("MISMATCHED_PHYSICAL_PATTERN"));
        ECOPatternSemanticsRegistry.unregister(id);
        register("null", 0, adapter((pattern, base) -> null));
        assertTrue(PatternSemanticAdapters.analyze(ordinaryPattern()).unsupportedReason().endsWith("NULL_RESULT"));
    }

    @Test
    void semanticListsAreCopiedAndExactAmountsSurviveBridge() {
        IPatternDetails pattern = ordinaryPattern();
        AEKey key = mock(AEKey.class);
        var source = mock(IPatternDetails.IInput.class);
        BigInteger amount = BigInteger.ONE.shiftLeft(90);
        List<ECOPatternSemantics.Input> inputs = new ArrayList<>();
        inputs.add(new ECOPatternSemantics.Input(source, key, amount, null, BigInteger.ZERO));
        List<GenericStack> outputs = new ArrayList<>(java.util.Arrays.asList(pattern.getOutputs()));
        ECOPatternSemantics semantics = new ECOPatternSemantics(pattern, null, inputs, outputs, List.of(), List.of(),
            ECOPatternSemantics.MatchingMode.EXACT, ECOPatternSemantics.ExecutionRestriction.NONE, true, true, null);
        inputs.clear();
        outputs.clear();
        register("amount", 0, adapter((ignored, base) -> semantics));
        var internal = PatternSemanticAdapters.analyze(pattern);
        assertEquals(amount, cn.dancingsnow.neoecoae.platform.JdkCompat.first(internal.consumedInputs()).amountPerPattern().toBigInteger());
        assertSame(source, cn.dancingsnow.neoecoae.platform.JdkCompat.first(internal.consumedInputs()).source());
        assertEquals(1, internal.producedOutputs().size());
        assertThrows(UnsupportedOperationException.class, semantics.consumedInputs()::clear);
        assertThrows(UnsupportedOperationException.class, internal.consumedInputs()::clear);
    }

    @Test
    void invalidOutputCannotProduceSuccessfulSemantics() {
        register("invalid", 0, adapter((pattern, base) -> new ECOPatternSemantics(pattern, null,
            List.of(), List.of(new GenericStack(mock(AEKey.class), -1)), List.of(), List.of(),
            ECOPatternSemantics.MatchingMode.EXACT, ECOPatternSemantics.ExecutionRestriction.NONE,
            true, true, null)));
        assertFalse(PatternSemanticAdapters.analyze(ordinaryPattern()).supported());
    }

    @Test
    void missingOutputAndInconsistentReturnsAreRejected() {
        IPatternDetails pattern = ordinaryPattern();
        assertThrows(NullPointerException.class, () -> new ECOPatternSemantics(pattern, null,
            List.of(), java.util.Arrays.asList((GenericStack) null), List.of(), List.of(),
            ECOPatternSemantics.MatchingMode.EXACT, ECOPatternSemantics.ExecutionRestriction.NONE,
            true, true, null));
        IPatternDetails.IInput source = mock(IPatternDetails.IInput.class);
        AEKey key = mock(AEKey.class);
        assertThrows(IllegalArgumentException.class,
            () -> new ECOPatternSemantics.Input(source, key, BigInteger.ONE, null, BigInteger.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new ECOPatternSemantics.Input(source, key, BigInteger.ONE, key, BigInteger.ZERO));
    }

    private ResourceLocation register(String path, int priority, ECOPatternSemanticAdapter adapter) {
        ResourceLocation id = new ResourceLocation("pattern_api_test", path);
        ECOPatternSemanticsRegistry.register(id, priority, adapter);
        registrations.add(id);
        return id;
    }

    private static ECOPatternSemanticAdapter adapter(
            BiFunction<IPatternDetails, ECOPatternSemantics, ECOPatternSemantics> analysis) {
        return new ECOPatternSemanticAdapter() {
            @Override
            public boolean supports(IPatternDetails pattern) {
                return true;
            }

            @Override
            public ECOPatternSemantics analyze(IPatternDetails pattern, ECOPatternSemantics base) {
                return analysis.apply(pattern, base);
            }
        };
    }

    private static ECOPatternSemantics unsupported(IPatternDetails pattern, String reason) {
        return ECOPatternSemantics.unsupported(pattern, null, reason);
    }

    private static IPatternDetails ordinaryPattern() {
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(mock(AEKey.class), 1)});
        return pattern;
    }
}
