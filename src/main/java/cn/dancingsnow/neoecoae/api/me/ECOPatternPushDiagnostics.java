package cn.dancingsnow.neoecoae.api.me;

import java.util.Set;

/** Read-only observations of the most recent AE2 push; never authorizes replay or material recovery. */
public interface ECOPatternPushDiagnostics {
    enum Reason {
        SEND_LIST_BUSY, NODE_INACTIVE, PATTERN_MISSING, CRAFTING_LOCKED,
        MACHINE_REJECTED, NO_EXTERNAL_TARGET, EXTERNAL_INPUT_UNSUPPORTED,
        BLOCKING_MODE, TARGET_REJECTED_INPUT, UNKNOWN
    }

    record Snapshot(String location, Set<Reason> reasons, int bufferedStacks, String sendDirection) {}

    /** Prevent an overriding provider from exposing an earlier call's rejection as the current result. */
    void neoecoae$clearPushDiagnostics();

    Snapshot neoecoae$getPushDiagnostics();
}
