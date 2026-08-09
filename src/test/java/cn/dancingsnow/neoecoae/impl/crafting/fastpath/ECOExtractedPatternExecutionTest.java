package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ECOExtractedPatternExecutionTest {
    @Test
    void acceptsSafeSingleOutputExecution() {
        assertTrue(ECOExtractedPatternExecution.isConcreteExecutionPolicySafe(1, true, true, true, true));
    }

    @Test
    void rejectsMultipleOutputs() {
        assertFalse(ECOExtractedPatternExecution.isConcreteExecutionPolicySafe(2, true, true, true, true));
    }

    @Test
    void rejectsAnyUnsafeConcreteComponent() {
        assertFalse(ECOExtractedPatternExecution.isConcreteExecutionPolicySafe(1, false, true, true, true));
        assertFalse(ECOExtractedPatternExecution.isConcreteExecutionPolicySafe(1, true, false, true, true));
        assertFalse(ECOExtractedPatternExecution.isConcreteExecutionPolicySafe(1, true, true, false, true));
        assertFalse(ECOExtractedPatternExecution.isConcreteExecutionPolicySafe(1, true, true, true, false));
    }
}
