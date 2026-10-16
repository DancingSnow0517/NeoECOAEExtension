package cn.dancingsnow.neoecoae.core.planner;

import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JointDemandQueueTest {
    @Test void requestedCoproductWaitsForAlreadyRequestedProducer() throws Exception {
        var queue = new JointDemandQueue<>(List.of("coproduct", "primary"), Map.of(),
            Map.of("coproduct", Set.of("primary")));
        queue.add("coproduct");
        queue.add("primary");
        queue.add("primary");
        assertEquals("primary", queue.poll(Cancellation.NONE));
        assertEquals("coproduct", queue.poll(Cancellation.NONE));
        assertTrue(queue.isEmpty());
    }

    @Test void lookaheadFindsRequestedConsumerOfTheSource() throws Exception {
        var queue = new JointDemandQueue<>(List.of("coproduct", "branch"),
            Map.of("source", Set.of("intermediate"), "intermediate", Set.of("branch")),
            Map.of("coproduct", Set.of("source")));
        queue.add("coproduct");
        queue.add("branch");
        assertEquals("branch", queue.poll(Cancellation.NONE));
        assertEquals("coproduct", queue.poll(Cancellation.NONE));
    }

    @Test void mutualWaitingFallsBackToOriginalOrderWithoutDroppingDemands() throws Exception {
        var queue = new JointDemandQueue<>(List.of("a", "b"), Map.of(),
            Map.of("a", Set.of("b"), "b", Set.of("a")));
        queue.add("b");
        queue.add("a");
        assertEquals("a", queue.poll(Cancellation.NONE));
        assertEquals("b", queue.poll(Cancellation.NONE));
        assertTrue(queue.isEmpty());
    }

    @Test void sourceCycleCannotActivateTheSameDemand() throws Exception {
        var queue = new JointDemandQueue<>(List.of("a"), Map.of("source", Set.of("a")),
            Map.of("a", Set.of("source")));
        queue.add("a");
        assertEquals("a", queue.poll(Cancellation.NONE));
        queue.add("new");
        assertEquals("new", queue.poll(Cancellation.NONE));
    }

    @Test void cancellationIsCheckedBeforePoppingTheNextDemand() throws Exception {
        var queue = new JointDemandQueue<>(List.of("a"), Map.of(), Map.of());
        queue.add("a");
        assertThrows(InterruptedException.class, () -> queue.poll(() -> { throw new InterruptedException(); }));
        assertEquals("a", queue.poll(Cancellation.NONE));
    }
}
