package cn.dancingsnow.neoecoae.impl.crafting.planner.provenance;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable material attribution passed from numeric planning to phase construction. */
public record ExecutionProvenance(Map<AEKey, Map<MaterialSource, PlannerAmount>> suppliers) {
    public static final ExecutionProvenance ABSENT = new ExecutionProvenance(Map.of());

    public ExecutionProvenance {
        Map<AEKey, Map<MaterialSource, PlannerAmount>> frozen = new LinkedHashMap<>();
        suppliers.forEach((key, sources) -> frozen.put(key,
            Collections.unmodifiableMap(new LinkedHashMap<>(sources))));
        suppliers = Collections.unmodifiableMap(frozen);
    }

    public Set<MaterialSource> suppliersOf(AEKey key) {
        return suppliers.getOrDefault(key, Map.of()).keySet();
    }

    public Map<MaterialSource, PlannerAmount> supplierAmountsOf(AEKey key) {
        return suppliers.getOrDefault(key, Map.of());
    }

    public boolean covers(AEKey key) {
        return !suppliersOf(key).isEmpty();
    }
}
