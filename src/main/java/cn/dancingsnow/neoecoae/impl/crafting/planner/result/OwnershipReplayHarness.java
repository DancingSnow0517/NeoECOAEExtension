package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import java.util.List;

/** Deterministically replays semantic ownership events against the slow reference implementation. */
public final class OwnershipReplayHarness {
    private OwnershipReplayHarness() { }

    public static ReferenceOwnershipLedger replay(ReferenceOwnershipLedger ledger, List<OwnershipEvent> events) {
        for (OwnershipEvent event : events) {
            switch (event.type()) {
                case DISPATCH_COMMITTED -> ledger.commitAccepted(event.consumed());
                case OUTPUT_RETURNED -> ledger.acceptOutput(event.resource(), event.amount());
                case OWNERSHIP_RELEASED -> ledger.releaseExternal(event.resource(), event.amount());
            }
        }
        return ledger;
    }
}
