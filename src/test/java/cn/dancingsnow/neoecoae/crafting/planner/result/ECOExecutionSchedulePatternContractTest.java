package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemantics;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticsRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOExecutionSchedulePatternContractTest {
    @Test void failedSpecialSemanticsCannotFallBackToRawPrimaryOutput() {
        var key = mock(AEKey.class);
        var pattern = mock(IPatternDetails.class);
        var output = new GenericStack(key, 1);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of(output));
        when(pattern.getPrimaryOutput()).thenReturn(output);
        var components = List.of(new ComponentPlanningResult(0, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, Map.of(key, 1L), Set.of(pattern), null, null,
            Map.of(), null, null));
        assertEquals(1, ECOExecutionSchedule.from(components, List.of(0), Map.of(pattern, 1L)).phases().size());
        var id = new ResourceLocation("eco_schedule_test", "restricted");
        ECOPatternSemanticsRegistry.register(id, 1000, new ECOPatternSemanticAdapter() {
            @Override public boolean supports(IPatternDetails details) { return details == pattern; }
            @Override public ECOPatternSemantics analyze(IPatternDetails details, ECOPatternSemantics base) {
                return ECOPatternSemantics.unsupported(details, base.physicalDefinition(), "REJECTED_SPECIAL_CONTRACT");
            }
        });
        try {
            assertThrows(IllegalStateException.class,
                () -> ECOExecutionSchedule.from(components, List.of(0), Map.of(pattern, 1L)));
        } finally {
            ECOPatternSemanticsRegistry.unregister(id);
        }
        assertEquals(1, ECOExecutionSchedule.from(components, List.of(0), Map.of(pattern, 1L)).phases().size());
    }
}
