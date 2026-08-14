package cn.dancingsnow.neoecoae.impl.crafting.planner.schedule;

import java.util.List;

public record ECORepeatedBlock<R>(List<ECOScheduledStep<R>> body, long repetitions)
    implements ECOScheduleEntry<R> {
    public ECORepeatedBlock {
        body = List.copyOf(body);
        if (body.isEmpty() || repetitions <= 0L) {
            throw new IllegalArgumentException("A repeated schedule block must have a body and repetitions");
        }
    }
}
