package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import java.util.*;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;

class ECOProcessingExecutionPatternTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }
    @Test void preservesRepeatedSparseSlotsAndScalesEveryInputBeforeInsertion() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var original = mock(IPatternDetails.class);
        doAnswer(call -> {
            KeyCounter[] input = call.getArgument(0);
            assertEquals(3, input[0].get(iron)); assertEquals(2, input[1].get(gold));
            IPatternDetails.PatternInputSink sink = call.getArgument(1);
            sink.pushInput(iron, 1); sink.pushInput(gold, 2); sink.pushInput(iron, 2);
            return null;
        }).when(original).pushInputsToExternalInventory(any(), any());
        var totals = new KeyCounter[]{new KeyCounter(), new KeyCounter()};
        totals[0].add(iron, 21); totals[1].add(gold, 14);
        var actual = new ArrayList<GenericStack>();
        new ECOProcessingExecutionPattern(original, 7).pushInputsToExternalInventory(totals,
                (key, amount) -> actual.add(new GenericStack(key, amount)));
        assertEquals(List.of(new GenericStack(iron, 7), new GenericStack(gold, 14), new GenericStack(iron, 14)), actual);
        assertEquals(21, totals[0].get(iron));
    }
    @Test void incompleteEmissionIsRejectedBeforeTouchingMachine() {
        var original = mock(IPatternDetails.class);
        var input = new KeyCounter(); input.add(AEItemKey.of(Items.IRON_INGOT), 6);
        var emitted = new ArrayList<GenericStack>();
        assertThrows(IllegalArgumentException.class, () -> new ECOProcessingExecutionPattern(original, 3)
                .pushInputsToExternalInventory(new KeyCounter[]{input}, (k, n) -> emitted.add(new GenericStack(k, n))));
        assertTrue(emitted.isEmpty());
    }
}
