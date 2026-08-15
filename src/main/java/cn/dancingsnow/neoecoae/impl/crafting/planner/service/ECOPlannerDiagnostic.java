package cn.dancingsnow.neoecoae.impl.crafting.planner.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A bounded, player-facing explanation for why ECO did not use a planning path. */
public enum ECOPlannerDiagnostic {
    COMPONENT_MISMATCH("component_mismatch"),
    COMPONENT_NO_PRODUCER("component_no_producer"),
    PROVIDER_SCOPED_NBT("provider_scoped_nbt"),
    STATEFUL_OUTPUT("stateful_output"),
    SUBSTITUTION_PATTERN("substitution_pattern"),
    DAMAGEABLE_INPUT("damageable_input"),
    VARIANT_EXPANSION_LIMIT("variant_expansion_limit"),
    GRAPH_SIZE_LIMIT("graph_size_limit"),
    CYCLE_SIZE_LIMIT("cycle_size_limit"),
    PATTERN_METADATA_MISSING("pattern_metadata_missing");

    private final String id;

    ECOPlannerDiagnostic(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.neoecoae.planning.diagnostic." + id;
    }

    public static ECOPlannerDiagnostic fromId(String id) {
        for (ECOPlannerDiagnostic diagnostic : values()) {
            if (diagnostic.id.equals(id)) {
                return diagnostic;
            }
        }
        return null;
    }

    /** Maps internal rejection context to a small, stable set suitable for a network payload. */
    public static List<ECOPlannerDiagnostic> classify(String context) {
        if (context == null || context.isBlank()) {
            return List.of();
        }
        String normalized = context.toLowerCase(Locale.ROOT);
        Set<ECOPlannerDiagnostic> result = new LinkedHashSet<>();
        if (normalized.contains("provider_scoped_nbt")) {
            result.add(PROVIDER_SCOPED_NBT);
        }
        if (normalized.contains("stateful_output")) {
            result.add(STATEFUL_OUTPUT);
        }
        if (normalized.contains("undeclared_dynamic_input")
            || normalized.contains("third-party pattern with dynamic")
            || normalized.contains("component_mismatch")) {
            result.add(COMPONENT_MISMATCH);
        }
        if (normalized.contains("no_producer")
            || normalized.contains("crafting_for_returned_null")) {
            result.add(COMPONENT_NO_PRODUCER);
        }
        if (normalized.contains("dynamic_smithing")
            || normalized.contains("substitution")) {
            result.add(SUBSTITUTION_PATTERN);
        }
        if (normalized.contains("damageable")
            || normalized.contains("durability")) {
            result.add(DAMAGEABLE_INPUT);
        }
        if (normalized.contains("variant_limit")
            || normalized.contains("input_slot_limit")
            || normalized.contains("alternative_unit_multiplier")) {
            result.add(VARIANT_EXPANSION_LIMIT);
        }
        if (normalized.contains("material_limit")
            || normalized.contains("operation_limit")
            || normalized.contains("materialized_operation_limit")) {
            result.add(GRAPH_SIZE_LIMIT);
        }
        if (normalized.contains("cycle_component_limit")) {
            result.add(CYCLE_SIZE_LIMIT);
        }
        if (normalized.contains("missing_primary")
            || normalized.contains("null_input")
            || normalized.contains("null_possible")
            || normalized.contains("pattern_returned")
            || normalized.contains("pattern_has")
            || normalized.contains("pattern_input")
            || normalized.contains("pattern_definition_missing")
            || normalized.contains("definition_read")
            || normalized.contains("output_read")
            || normalized.contains("input_read")
            || normalized.contains("metadata")) {
            result.add(PATTERN_METADATA_MISSING);
        }
        return List.copyOf(result);
    }
}
