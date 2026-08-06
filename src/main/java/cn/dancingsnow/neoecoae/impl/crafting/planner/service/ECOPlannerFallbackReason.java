package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import java.util.Arrays;

/** The server-authoritative reason an AE2 plan did not use ECO's planner. */
public enum ECOPlannerFallbackReason {
    FAST_PATH("fast_path"),
    DYNAMIC_SMITHING("dynamic_smithing"),
    NO_ECO_HOST("no_eco_host"),
    SNAPSHOT_REJECTED("snapshot_rejected"),
    PATTERN_INCOMPATIBLE("pattern_incompatible"),
    SNAPSHOT_LIMIT_EXCEEDED("snapshot_limit_exceeded"),
    FALLBACK_SETUP_FAILED("fallback_setup_failed"),
    SOLVER_NO_ROUTE("solver_no_route"),
    SOLVER_BUDGET_EXHAUSTED("solver_budget_exhausted"),
    ASSEMBLY_REJECTED("assembly_rejected"),
    CRAFT_LESS_NO_CRAFTABLE("craft_less_no_craftable"),
    PRECISE_PATH_FAILED("precise_path_failed"),
    DIFFERENTIAL_MISMATCH("differential_mismatch"),
    PLANNING_FAILURE("planning_failure");

    private final String id;

    ECOPlannerFallbackReason(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.neoecoae.planning.reason." + id;
    }

    public static ECOPlannerFallbackReason fromId(String id) {
        return Arrays.stream(values())
            .filter(reason -> reason.id.equals(id))
            .findFirst()
            .orElse(PLANNING_FAILURE);
    }
}
