package cn.dancingsnow.neoecoae.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternContractReaderTest {
    @Test
    void wellFormedPatternIsReadOnce() {
        AEKey in = mock(AEKey.class);
        AEKey out = mock(AEKey.class);
        var slot = input(new GenericStack[] {new GenericStack(in, 2)}, 3);
        IPatternDetails pattern = pattern(new IPatternDetails.IInput[] {slot}, List.of(new GenericStack(out, 1)));

        var view = PatternContractReader.read(pattern);

        assertTrue(view.wellFormed());
        assertEquals(List.of(new GenericStack(out, 1)), view.outputs());
        assertEquals(1, view.inputs().size());
        assertEquals(new GenericStack(in, 2), view.inputs().getFirst().primary());
        assertEquals(3L, view.inputs().getFirst().multiplier());
        verify(pattern, times(1)).getInputs();
        verify(pattern, times(1)).getOutputs();
        verify(slot, times(1)).getPossibleInputs();
    }

    @Test
    void malformedEntriesAreDroppedAndFlagged() {
        AEKey key = mock(AEKey.class);
        var good = input(new GenericStack[] {new GenericStack(key, 1)}, 1);
        var nullChoice = input(new GenericStack[] {null}, 1);
        var empty = input(new GenericStack[0], 1);
        IPatternDetails pattern = pattern(new IPatternDetails.IInput[] {good, null, nullChoice, empty},
            Arrays.asList(new GenericStack(key, 1), null));

        var view = PatternContractReader.read(pattern);

        assertFalse(view.wellFormed());
        assertEquals(List.of(new GenericStack(key, 1)), view.outputs());
        assertEquals(1, view.inputs().size());
        assertSame(good.getPossibleInputs()[0], view.inputs().getFirst().primary());
    }

    @Test
    void throwingPatternYieldsEmptyMalformedView() {
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getOutputs()).thenThrow(new IllegalStateException("broken addon"));

        var view = PatternContractReader.read(pattern);

        assertFalse(view.wellFormed());
        assertTrue(view.outputs().isEmpty());
        assertTrue(view.inputs().isEmpty());
    }

    private static IPatternDetails.IInput input(GenericStack[] choices, long multiplier) {
        IPatternDetails.IInput input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(choices);
        when(input.getMultiplier()).thenReturn(multiplier);
        return input;
    }

    private static IPatternDetails pattern(IPatternDetails.IInput[] inputs, List<GenericStack> outputs) {
        IPatternDetails pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(outputs);
        return pattern;
    }
}
