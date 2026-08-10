package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ECOExtractedPatternExecutionTest {
    @Test
    void attemptsMetadataOnlyWhenEveryPrerequisiteIsMet() {
        assertTrue(ECOExtractedPatternExecution.shouldAttemptFastPathMetadata(true, true, false, true, true));
    }

    @Test
    void skipsMetadataForEveryEarlyRejection() {
        assertFalse(ECOExtractedPatternExecution.shouldAttemptFastPathMetadata(true, false, false, true, true));
        assertFalse(ECOExtractedPatternExecution.shouldAttemptFastPathMetadata(true, true, true, true, true));
        assertFalse(ECOExtractedPatternExecution.shouldAttemptFastPathMetadata(false, true, false, true, true));
        assertFalse(ECOExtractedPatternExecution.shouldAttemptFastPathMetadata(true, true, false, false, true));
        assertFalse(ECOExtractedPatternExecution.shouldAttemptFastPathMetadata(true, true, false, true, false));
    }
}
